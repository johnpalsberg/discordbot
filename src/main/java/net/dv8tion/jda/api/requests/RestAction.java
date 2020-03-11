/*
 * Copyright 2015-2019 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.api.requests;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.ContextException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.utils.concurrent.DelayedCompletableFuture;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.dv8tion.jda.internal.requests.restaction.operator.DelayRestAction;
import net.dv8tion.jda.internal.requests.restaction.operator.FlatMapRestAction;
import net.dv8tion.jda.internal.requests.restaction.operator.MapRestAction;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.ContextRunnable;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A class representing a terminal between the user and the discord API.
 * <br>This is used to offer users the ability to decide how JDA should limit a Request.
 *
 * <p>Methods that return an instance of RestAction require an additional step
 * to complete the execution. Thus the user needs to append a follow-up method.
 *
 * <p>A default RestAction is issued with the following operations:
 * <ul>
 *     <li>{@link #queue()}, {@link #queue(Consumer)}, {@link #queue(Consumer, Consumer)}
 *     <br>The fastest and most simplistic way to execute a RestAction is to queue it.
 *     <br>This method has two optional callback functions, one with the generic type and another with a failure exception.</li>
 *
 *     <li>{@link #submit()}, {@link #submit(boolean)}
 *     <br>Provides a Future representing the pending request.
 *     <br>An optional parameter of type boolean can be passed to disable automated rate limit handling. (not recommended)</li>
 *
 *     <li>{@link #complete()}, {@link #complete(boolean)}
 *     <br>Blocking execution building up on {@link #submit()}.
 *     <br>This will simply block the thread and return the Request result, or throw an exception.
 *     <br>An optional parameter of type boolean can be passed to disable automated rate limit handling. (not recommended)</li>
 * </ul>
 *
 * The most efficient way to use a RestAction is by using the asynchronous {@link #queue()} operations.
 * <br>These allow users to provide success and failure callbacks which will be called at a convenient time.
 *
 * <h2>Planning Execution</h2>
 * To <u>schedule</u> a RestAction we provide both {@link #queue()} and {@link #complete()} versions that
 * will be executed by a {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} after a
 * specified delay:
 * <ul>
 *     <li>{@link #queueAfter(long, TimeUnit)}
 *     <br>Schedules a call to {@link #queue()} with default callback {@link java.util.function.Consumer Consumers} to be executed after the specified {@code delay}.
 *     <br>The {@link java.util.concurrent.TimeUnit TimeUnit} is used to convert the provided long into a delay time.
 *     <br>Example: {@code queueAfter(1, TimeUnit.SECONDS);}
 *     <br>will call {@link #queue()} <b>1 second</b> later.</li>
 *
 *     <li>{@link #submitAfter(long, TimeUnit)}
 *     <br>This returns a {@link java.util.concurrent.ScheduledFuture ScheduledFuture} which
 *         can be joined into the current Thread using {@link java.util.concurrent.ScheduledFuture#get()}
 *     <br>The blocking call to {@code submitAfter(delay, unit).get()} will return
 *         the value processed by a call to {@link #complete()}</li>
 *
 *     <li>{@link #completeAfter(long, TimeUnit)}
 *     <br>This operation simply sleeps for the given delay and will call {@link #complete()}
 *         once finished sleeping.</li>
 * </ul>
 *
 * <p>All of those operations provide overloads for optional parameters such as a custom
 * {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} instead of using the default
 * global JDA executor. Specifically {@link #queueAfter(long, TimeUnit)} has overloads
 * to provide a success and/or failure callback due to the returned {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
 * not being able to provide the response values of the {@link #queue()} callbacks.
 *
 * <h1>Using RestActions</h1>
 * The most common way to use a RestAction is not using the returned value.
 * <br>For instance sending messages usually means you will not require to view the message once
 * it was sent. Thus we can simply use the <b>asynchronous</b> {@link #queue()} operation which will
 * be executed on a rate limit worker thread in the background, without blocking your current thread:
 * <pre><code>
 *      {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} channel = event.getChannel();
 *     {@literal RestAction<Message>} action = channel.sendMessage("Hello World");
 *      action.{@link #queue() queue()}; // Execute the rest action asynchronously
 * </code></pre>
 *
 * <p>Sometimes it is important to access the response value, possibly to modify it later.
 * <br>Now we have two options to actually access the response value, either using an asynchronous
 * callback {@link java.util.function.Consumer Consumer} or the (not recommended) {@link #complete()} which will block
 * the current thread until the response has been processed and joins with the current thread.
 *
 * <h2>Example Queue: (recommended)</h2>
 * <pre><code>
 *     {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} channel = event.getChannel();
 *     final long time = System.currentTimeMillis();
 *    {@literal RestAction<Message>} action = channel.sendMessage("Calculating Response Time...");
 *     {@link java.util.function.Consumer Consumer}{@literal <Message>} callback = (message) {@literal ->  {
 *        Message m = message; // ^This is a lambda parameter!^
 *        m.editMessage("Response Time: " + (System.currentTimeMillis() - time) + "ms").queue();
 *        // End with queue() to not block the callback thread!
 *      }};
 *     // You can also inline this with the queue parameter: action.queue(m {@literal ->} m.editMessage(...).queue());
 *     action.{@link #queue(Consumer) queue(callback)};
 * </code></pre>
 *
 * <h2>Example Complete:</h2>
 * <pre><code>
 *     {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} channel = event.getChannel();
 *     final long time = System.currentTimeMillis();
 *    {@literal RestAction<Message>} action = channel.sendMessage("Calculating Response Time...");
 *     Message message = action.{@link #complete() complete()};
 *     message.editMessage("Response Time: " + (System.currentTimeMillis() - time) + "ms").queue();
 *     // End with {@link #queue() queue()} to not block the callback thread!
 * </code></pre>
 *
 * <h2>Example Planning:</h2>
 * <pre><code>
 *     {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} channel = event.getChannel();
 *    {@literal RestAction<Message>} action = channel.sendMessage("This message will destroy itself in 5 seconds!");
 *     action.queue((message) {@literal ->} message.delete().{@link #queueAfter(long, TimeUnit) queueAfter(5, TimeUnit.SECONDS)});
 * </code></pre>
 *
 * <p><b>Developer Note:</b> It is generally a good practice to use asynchronous logic because blocking threads requires resources
 * which can be avoided by using callbacks over blocking operations:
 * <br>{@link #queue(Consumer)} {@literal >} {@link #complete()}
 *
 * <p>There is a dedicated <a href="https://github.com/DV8FromTheWorld/JDA/wiki/7)-Using-RestAction" target="_blank">wiki page</a>
 * for RestActions that can be useful for learning.
 *
 * @param <T>
 *        The generic response type for this RestAction
 *
 * @since 3.0
 */
public interface RestAction<T>
{
    /**
     * If enabled this will pass a {@link net.dv8tion.jda.api.exceptions.ContextException ContextException}
     * as root-cause to all failure consumers.
     * <br>This might cause performance decrease due to the creation of exceptions for <b>every</b> execution.
     *
     * <p>It is recommended to pass a context consumer as failure manually using {@code queue(success, ContextException.here(failure))}
     *
     * @param  enable
     *         True, if context should be passed to all failure consumers
     */
    static void setPassContext(boolean enable)
    {
        RestActionImpl.setPassContext(enable);
    }

    /**
     * Whether RestActions will use {@link net.dv8tion.jda.api.exceptions.ContextException ContextException}
     * automatically to keep track of the caller context.
     * <br>If set to {@code true} this can cause performance drops due to the creation of stack-traces on execution.
     *
     * @return True, if RestActions will keep track of context automatically
     *
     * @see    #setPassContext(boolean)
     */
    static boolean isPassContext()
    {
        return RestActionImpl.isPassContext();
    }

    /**
     * The default failure callback used when none is provided in {@link #queue(Consumer, Consumer)}.
     *
     * @param callback
     *        The fallback to use, or null to ignore failures (not recommended)
     */
    static void setDefaultFailure(@Nullable final Consumer<? super Throwable> callback)
    {
        RestActionImpl.setDefaultFailure(callback);
    }

    /**
     * The default success callback used when none is provided in {@link #queue(Consumer, Consumer)} or {@link #queue(Consumer)}.
     *
     * @param callback
     *        The fallback to use, or null to ignore success
     */
    static void setDefaultSuccess(@Nullable final Consumer<Object> callback)
    {
        RestActionImpl.setDefaultSuccess(callback);
    }

    /**
     * The default failure callback used when none is provided in {@link #queue(Consumer, Consumer)}.
     *
     * @return The fallback consumer
     */
    @Nonnull
    static Consumer<? super Throwable> getDefaultFailure()
    {
        return RestActionImpl.getDefaultFailure();
    }

    /**
     * The default success callback used when none is provided in {@link #queue(Consumer, Consumer)} or {@link #queue(Consumer)}.
     *
     * @return The fallback consumer
     */
    @Nonnull
    static Consumer<Object> getDefaultSuccess()
    {
        return RestActionImpl.getDefaultSuccess();
    }

    /**
     * The current JDA instance
     *
     * @return The corresponding JDA instance
     */
    @Nonnull
    JDA getJDA();

    /**
     * Sets the last-second checks before finally executing the http request in the queue.
     * <br>If the provided supplier evaluates to {@code false} or throws an exception this will not be finished.
     * When an exception is thrown from the supplier it will be provided to the failure callback.
     *
     * @param  checks
     *         The checks to run before executing the request, or {@code null} to run no checks
     *
     * @return The current RestAction for chaining convenience
     */
    @Nonnull
    RestAction<T> setCheck(@Nullable BooleanSupplier checks);

    /**
     * Submits a Request for execution.
     * <br>Using the default callback functions:
     * {@link #setDefaultSuccess(Consumer)} and {@link #setDefaultFailure(Consumer)}
     *
     * <p>To access the response you can use {@link #queue(java.util.function.Consumer)}
     * and to handle failures use {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}.
     *
     * <p><b>This method is asynchronous</b>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public static void sendMessage(MessageChannel channel, String content)
     * {
     *     // sendMessage returns "MessageAction" which is a specialization for "RestAction<Message>"
     *     RestAction<Message> action = channel.sendMessage(content);
     *     // call queue() to send the message off to discord.
     *     action.queue();
     * }
     * }</pre>
     *
     * @see net.dv8tion.jda.api.entities.MessageChannel#sendMessage(java.lang.CharSequence) MessageChannel.sendMessage(CharSequence)
     * @see net.dv8tion.jda.api.requests.restaction.MessageAction MessageAction
     * @see #queue(java.util.function.Consumer) queue(Consumer)
     * @see #queue(java.util.function.Consumer, java.util.function.Consumer) queue(Consumer, Consumer)
     */
    default void queue()
    {
        queue(null);
    }

    /**
     * Submits a Request for execution.
     * <br>Using the default failure callback function.
     *
     * <p>To handle failures use {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}.
     *
     * <p><b>This method is asynchronous</b>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public static void sendPrivateMessage(User user, String content)
     * {
     *     // The "<PrivateChannel>" is the response type for the parameter in the success callback
     *     RestAction<PrivateChannel> action = user.openPrivateChannel();
     *     // "channel" is the identifier we use to access the channel of the response
     *     // this is like the "user" we declared above, just a name for the function parameter
     *     action.queue((channel) -> channel.sendMessage(content).queue());
     * }
     * }</pre>
     *
     * @param  success
     *         The success callback that will be called at a convenient time
     *         for the API. (can be null)
     *
     * @see    #queue(java.util.function.Consumer, java.util.function.Consumer) queue(Consumer, Consumer)
     */
    default void queue(@Nullable Consumer<? super T> success)
    {
        queue(success, null);
    }

    /**
     * Submits a Request for execution.
     *
     * <p><b>This method is asynchronous</b>
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public static void sendPrivateMessage(JDA jda, String userId, String content)
     * {
     *     // Retrieve the user by their id
     *     RestAction<User> action = jda.retrieveUserById(userId);
     *     action.queue(
     *         // Handle success if the user exists
     *         (user) -> user.openPrivateChannel().queue(
     *             (channel) -> channel.sendMessage(content).queue()),
     *
     *         // Handle failure if the user does not exist (or another issue appeared)
     *         (error) -> error.printStackTrace()
     *     );
     *
     *     // Alternatively use submit() to remove nested callbacks
     * }
     * }</pre>
     *
     * @param  success
     *         The success callback that will be called at a convenient time
     *         for the API. (can be null to use default)
     * @param  failure
     *         The failure callback that will be called if the Request
     *         encounters an exception at its execution point. (can be null to use default)
     *
     * @see    #submit()
     */
    void queue(@Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure);

    /**
     * Blocks the current Thread and awaits the completion
     * of an {@link #submit()} request.
     * <br>Used for synchronous logic.
     *
     * <p><b>This might throw {@link java.lang.RuntimeException RuntimeExceptions}</b>
     *
     * @throws IllegalStateException
     *         If used within a {@link #queue(Consumer, Consumer) queue(...)} callback
     *
     * @return The response value
     */
    default T complete()
    {
        try
        {
            return complete(true);
        }
        catch (RateLimitedException e)
        {
            //This is so beyond impossible, but on the off chance that the laws of nature are rewritten
            // after the writing of this code, I'm placing this here.
            //Better safe than sorry?
            throw new AssertionError(e);
        }
    }

    /**
     * Blocks the current Thread and awaits the completion
     * of an {@link #submit()} request.
     * <br>Used for synchronous logic.
     *
     * @param  shouldQueue
     *         Whether this should automatically handle rate limitations (default true)
     *
     * @throws IllegalStateException
     *         If used within a {@link #queue(Consumer, Consumer) queue(...)} callback
     * @throws RateLimitedException
     *         If we were rate limited and the {@code shouldQueue} is false.
     *         Use {@link #complete()} to avoid this Exception.
     *
     * @return The response value
     */
    T complete(boolean shouldQueue) throws RateLimitedException;

    /**
     * Submits a Request for execution and provides a {@link java.util.concurrent.CompletableFuture CompletableFuture}
     * representing its completion task.
     * <br>Cancelling the returned Future will result in the cancellation of the Request!
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public static void sendPrivateMessage(JDA jda, String userId, String content)
     * {
     *     // Retrieve the user by their id
     *     RestAction<User> action = jda.retrieveUserById(userId);
     *     action.submit() // CompletableFuture<User>
     *           // Handle success if the user exists
     *           .thenCompose((user) -> user.openPrivateChannel().submit()) // CompletableFuture<PrivateChannel>
     *           .thenCompose((channel) -> channel.sendMessage(content).submit()) // CompletableFuture<Void>
     *           .whenComplete((v, error) -> {
     *               // Handle failure if the user does not exist (or another issue appeared)
     *               if (error != null) error.printStackTrace();
     *           });
     * }
     * }</pre>
     *
     * @return Never-null {@link java.util.concurrent.CompletableFuture CompletableFuture} representing the completion promise
     */
    @Nonnull
    default CompletableFuture<T> submit()
    {
        return submit(true);
    }

    /**
     * Submits a Request for execution and provides a {@link java.util.concurrent.CompletableFuture CompletableFuture}
     * representing its completion task.
     * <br>Cancelling the returned Future will result in the cancellation of the Request!
     *
     * @param  shouldQueue
     *         Whether the Request should automatically handle rate limitations. (default true)
     *
     * @return Never-null {@link java.util.concurrent.CompletableFuture CompletableFuture} task representing the completion promise
     */
    @Nonnull
    CompletableFuture<T> submit(boolean shouldQueue);

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will apply
     * the map function on successful execution.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<String> retrieveMemberNickname(Guild guild, String userId) {
     *     return guild.retrieveMemberById(userId)
     *                 .map(Member::getNickname);
     * }
     * }</pre>
     *
     * @param  map
     *         The mapping function to apply to the action result
     *
     * @param  <O>
     *         The target output type
     *
     * @return RestAction for the mapped type
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default <O> RestAction<O> map(@Nonnull Function<? super T, ? extends O> map)
    {
        Checks.notNull(map, "Function");
        return new MapRestAction<>(this, map);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will apply
     * the map function on successful execution. This will compute the result of both RestActions.
     * <br>The returned RestAction must not be null!
     * To terminate the execution chain on a specific condition you can use {@link #flatMap(Predicate, Function)}.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<Void> initializeGiveaway(Guild guild, String channelName) {
     *     return guild.createTextChannel(channelName)
     *          .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.MESSAGE_WRITE)) // deny write for everyone
     *          .addPermissionOverride(guild.getSelfMember(), EnumSet.of(Permission.MESSAGE_WRITE), null) // allow for self user
     *          .flatMap((channel) -> channel.sendMessage("React to enter giveaway!")) // send message
     *          .flatMap((message) -> message.addReaction(REACTION)); // add reaction
     * }
     * }</pre>
     *
     * @param  flatMap
     *         The mapping function to apply to the action result, must return a RestAction
     *
     * @param  <O>
     *         The target output type
     *
     * @return RestAction for the mapped type
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default <O> RestAction<O> flatMap(@Nonnull Function<? super T, ? extends RestAction<O>> flatMap)
    {
        return flatMap(null, flatMap);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will apply
     * the map function on successful execution. This will compute the result of both RestActions.
     * <br>The provided RestAction must not be null!
     *
     * <h2>Example</h2>
     * <pre>{@code
     * private static final int MAX_COUNT = 1000;
     * public void updateCount(MessageChannel channel, String messageId, int count) {
     *     channel.retrieveMessageById(messageId) // retrieve message for check
     *         .map(Message::getContentRaw) // get content of the message
     *         .map(Integer::parseInt) // convert it to an int
     *         .flatMap(
     *             (currentCount) -> currentCount + count <= MAX_COUNT, // Only edit if new count does not exceed maximum
     *             (currentCount) -> channel.editMessageById(messageId, String.valueOf(currentCount + count)) // edit message
     *         )
     *         .map(Message::getContentRaw) // get content of the message
     *         .map(Integer::parseInt) // convert it to an int
     *         .queue((newCount) -> System.out.println("Updated count to " + newCount));
     * }
     * }</pre>
     *
     * @param  condition
     *         A condition predicate that decides whether to apply the flat map operator or not
     * @param  flatMap
     *         The mapping function to apply to the action result, must return a RestAction
     *
     * @param  <O>
     *         The target output type
     *
     * @return RestAction for the mapped type
     *
     * @see    #flatMap(Function)
     * @see    #map(Function)
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default <O> RestAction<O> flatMap(@Nullable Predicate<? super T> condition, @Nonnull Function<? super T, ? extends RestAction<O>> flatMap)
    {
        Checks.notNull(flatMap, "Function");
        return new FlatMapRestAction<>(this, condition, flatMap);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will delay its result by the provided delay.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<Void> selfDestruct(MessageChannel channel, String content) {
     *     return channel.sendMessage("The following message will destroy itself in 1 minute!")
     *         .delay(Duration.ofSeconds(10)) // edit 10 seconds later
     *         .flatMap((it) -> it.editMessage(content))
     *         .delay(Duration.ofMinutes(1)) // delete 1 minute later
     *         .flatMap(Message::delete);
     * }
     * }</pre>
     *
     * @param  duration
     *         The delay
     *
     * @return RestAction with delay
     *
     * @see    #queueAfter(long, TimeUnit)
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default RestAction<T> delay(@Nonnull Duration duration)
    {
        return delay(duration, null);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will delay its result by the provided delay.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<Void> selfDestruct(MessageChannel channel, String content) {
     *     return channel.sendMessage("The following message will destroy itself in 1 minute!")
     *         .delay(Duration.ofSeconds(10), scheduler) // edit 10 seconds later
     *         .flatMap((it) -> it.editMessage(content))
     *         .delay(Duration.ofMinutes(1), scheduler) // delete 1 minute later
     *         .flatMap(Message::delete);
     * }
     * }</pre>
     *
     * @param  duration
     *         The delay
     * @param  scheduler
     *         The scheduler to use, null to use {@link JDA#getRateLimitPool()}
     *
     * @return RestAction with delay
     *
     * @see    #queueAfter(long, TimeUnit, ScheduledExecutorService)
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default RestAction<T> delay(@Nonnull Duration duration, @Nullable ScheduledExecutorService scheduler)
    {
        Checks.notNull(duration, "Duration");
        return new DelayRestAction<>(this, TimeUnit.MILLISECONDS, duration.toMillis(), scheduler);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will delay its result by the provided delay.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<Void> selfDestruct(MessageChannel channel, String content) {
     *     return channel.sendMessage("The following message will destroy itself in 1 minute!")
     *         .delay(10, SECONDS) // edit 10 seconds later
     *         .flatMap((it) -> it.editMessage(content))
     *         .delay(1, MINUTES) // delete 1 minute later
     *         .flatMap(Message::delete);
     * }
     * }</pre>
     *
     * @param  delay
     *         The delay value
     * @param  unit
     *         The time unit for the delay value
     *
     * @return RestAction with delay
     *
     * @see    #queueAfter(long, TimeUnit)
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default RestAction<T> delay(long delay, @Nonnull TimeUnit unit)
    {
        return delay(delay, unit, null);
    }

    /**
     * Intermediate operator that returns a modified RestAction.
     *
     * <p>This does not modify this instance but returns a new RestAction which will delay its result by the provided delay.
     *
     * <h2>Example</h2>
     * <pre>{@code
     * public RestAction<Void> selfDestruct(MessageChannel channel, String content) {
     *     return channel.sendMessage("The following message will destroy itself in 1 minute!")
     *         .delay(10, SECONDS, scheduler) // edit 10 seconds later
     *         .flatMap((it) -> it.editMessage(content))
     *         .delay(1, MINUTES, scheduler) // delete 1 minute later
     *         .flatMap(Message::delete);
     * }
     * }</pre>
     *
     * @param  delay
     *         The delay value
     * @param  unit
     *         The time unit for the delay value
     * @param  scheduler
     *         The scheduler to use, null to use {@link JDA#getRateLimitPool()}
     *
     * @return RestAction with delay
     *
     * @see    #queueAfter(long, TimeUnit, ScheduledExecutorService)
     *
     * @since  4.1.1
     */
    @Nonnull
    @CheckReturnValue
    default RestAction<T> delay(long delay, @Nonnull TimeUnit unit, @Nullable ScheduledExecutorService scheduler)
    {
        Checks.notNull(unit, "TimeUnit");
        return new DelayRestAction<>(this, unit, delay, scheduler);
    }

    /**
     * Schedules a call to {@link #queue()} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link CompletableFuture CompletableFuture} representing the task.
     *
     * <p>Similar to {@link #queueAfter(long, TimeUnit)} but does not require callbacks to be passed.
     * Continuations of {@link CompletableFuture} can be used instead.
     *
     * <p>The global JDA RateLimit {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService}
     * is used for this operation.
     * <br>You can provide your own Executor using {@link #submitAfter(long, java.util.concurrent.TimeUnit, java.util.concurrent.ScheduledExecutorService)}!
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit is {@code null}
     *
     * @return {@link DelayedCompletableFuture DelayedCompletableFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default DelayedCompletableFuture<T> submitAfter(long delay, @Nonnull TimeUnit unit)
    {
        return submitAfter(delay, unit, null);
    }

    /**
     * Schedules a call to {@link #queue()} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link CompletableFuture CompletableFuture} representing the task.
     *
     * <p>Similar to {@link #queueAfter(long, TimeUnit)} but does not require callbacks to be passed.
     * Continuations of {@link CompletableFuture} can be used instead.
     *
     * <p>The specified {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  executor
     *         The {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that should be used
     *         to schedule this operation, or null to use the default
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit is {@code null}
     *
     * @return {@link DelayedCompletableFuture DelayedCompletableFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default DelayedCompletableFuture<T> submitAfter(long delay, @Nonnull TimeUnit unit, @Nullable ScheduledExecutorService executor)
    {
        Checks.notNull(unit, "TimeUnit");
        if (executor == null)
            executor = getJDA().getRateLimitPool();
        return DelayedCompletableFuture.make(executor, delay, unit,
                (task) -> {
                    final Consumer<? super Throwable> onFailure;
                    if (isPassContext())
                        onFailure = ContextException.here(task::completeExceptionally);
                    else
                        onFailure = task::completeExceptionally;
                    return new ContextRunnable<T>(() -> queue(task::complete, onFailure));
                });
    }

    /**
     * Blocks the current Thread for the specified delay and calls {@link #complete()}
     * when delay has been reached.
     * <br>If the specified delay is negative this action will execute immediately. (see: {@link TimeUnit#sleep(long)})
     *
     * @param  delay
     *         The delay after which to execute a call to {@link #complete()}
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} which should be used
     *         (this will use {@link java.util.concurrent.TimeUnit#sleep(long) unit.sleep(delay)})
     *
     * @throws java.lang.IllegalArgumentException
     *         If the specified {@link java.util.concurrent.TimeUnit TimeUnit} is {@code null}
     * @throws java.lang.RuntimeException
     *         If the sleep operation is interrupted
     *
     * @return The response value
     */
    default T completeAfter(long delay, @Nonnull TimeUnit unit)
    {
        Checks.notNull(unit, "TimeUnit");
        try
        {
            unit.sleep(delay);
            return complete();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Schedules a call to {@link #queue()} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>This operation gives no access to the response value.
     * <br>Use {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer)} to access
     * the success consumer for {@link #queue(java.util.function.Consumer)}!
     *
     * <p>The global JDA {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     * <br>You can provide your own Executor with {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.concurrent.ScheduledExecutorService)}
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit)
    {
        return queueAfter(delay, unit, null, null, null);
    }

    /**
     * Schedules a call to {@link #queue(java.util.function.Consumer)} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>This operation gives no access to the failure callback.
     * <br>Use {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer, java.util.function.Consumer)} to access
     * the failure consumer for {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}!
     *
     * <p>The global JDA {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     * <br>You can provide your own Executor with {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer, java.util.concurrent.ScheduledExecutorService)}
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  success
     *         The success {@link java.util.function.Consumer Consumer} that should be called
     *         once the {@link #queue(java.util.function.Consumer)} operation completes successfully.
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit, @Nullable Consumer<? super T> success)
    {
        return queueAfter(delay, unit, success, null, null);
    }

    /**
     * Schedules a call to {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}
     * to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>The global JDA {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     * <br>You provide your own Executor with {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer, java.util.function.Consumer, java.util.concurrent.ScheduledExecutorService)}
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  success
     *         The success {@link java.util.function.Consumer Consumer} that should be called
     *         once the {@link #queue(java.util.function.Consumer, java.util.function.Consumer)} operation completes successfully.
     * @param  failure
     *         The failure {@link java.util.function.Consumer Consumer} that should be called
     *         in case of an error of the {@link #queue(java.util.function.Consumer, java.util.function.Consumer)} operation.
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure)
    {
        return queueAfter(delay, unit, success, failure, null);
    }

    /**
     * Schedules a call to {@link #queue()} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>This operation gives no access to the response value.
     * <br>Use {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer)} to access
     * the success consumer for {@link #queue(java.util.function.Consumer)}!
     *
     * <p>The specified {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  executor
     *         The Non-null {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that should be used
     *         to schedule this operation
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit or ScheduledExecutorService is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit, @Nullable ScheduledExecutorService executor)
    {
        return queueAfter(delay, unit, null, null, executor);
    }

    /**
     * Schedules a call to {@link #queue(java.util.function.Consumer)} to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>This operation gives no access to the failure callback.
     * <br>Use {@link #queueAfter(long, java.util.concurrent.TimeUnit, java.util.function.Consumer, java.util.function.Consumer)} to access
     * the failure consumer for {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}!
     *
     * <p>The specified {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  success
     *         The success {@link java.util.function.Consumer Consumer} that should be called
     *         once the {@link #queue(java.util.function.Consumer)} operation completes successfully.
     * @param  executor
     *         The Non-null {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that should be used
     *         to schedule this operation
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit or ScheduledExecutorService is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable ScheduledExecutorService executor)
    {
        return queueAfter(delay, unit, success, null, executor);
    }

    /**
     * Schedules a call to {@link #queue(java.util.function.Consumer, java.util.function.Consumer)}
     * to be executed after the specified {@code delay}.
     * <br>This is an <b>asynchronous</b> operation that will return a
     * {@link java.util.concurrent.ScheduledFuture ScheduledFuture} representing the task.
     *
     * <p>The specified {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} is used for this operation.
     *
     * @param  delay
     *         The delay after which this computation should be executed, negative to execute immediately
     * @param  unit
     *         The {@link java.util.concurrent.TimeUnit TimeUnit} to convert the specified {@code delay}
     * @param  success
     *         The success {@link java.util.function.Consumer Consumer} that should be called
     *         once the {@link #queue(java.util.function.Consumer, java.util.function.Consumer)} operation completes successfully.
     * @param  failure
     *         The failure {@link java.util.function.Consumer Consumer} that should be called
     *         in case of an error of the {@link #queue(java.util.function.Consumer, java.util.function.Consumer)} operation.
     * @param  executor
     *         The Non-null {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} that should be used
     *         to schedule this operation
     *
     * @throws java.lang.IllegalArgumentException
     *         If the provided TimeUnit or ScheduledExecutorService is {@code null}
     *
     * @return {@link java.util.concurrent.ScheduledFuture ScheduledFuture}
     *         representing the delayed operation
     */
    @Nonnull
    default ScheduledFuture<?> queueAfter(long delay, @Nonnull TimeUnit unit, @Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure, @Nullable ScheduledExecutorService executor)
    {
        Checks.notNull(unit, "TimeUnit");
        if (executor == null)
            executor = getJDA().getRateLimitPool();

        final Consumer<? super Throwable> onFailure;
        if (isPassContext())
            onFailure = ContextException.here(failure == null ? getDefaultFailure() : failure);
        else
            onFailure = failure;

        Runnable task = new ContextRunnable<Void>(() -> queue(success, onFailure));
        return executor.schedule(task, delay, unit);
    }
}
