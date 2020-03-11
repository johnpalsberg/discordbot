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

package net.dv8tion.jda.internal.requests.ratelimit;

import net.dv8tion.jda.api.requests.Request;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.internal.requests.RateLimiter;
import net.dv8tion.jda.internal.requests.Requester;
import net.dv8tion.jda.internal.requests.Route;
import okhttp3.Headers;
import org.jetbrains.annotations.Contract;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/*

** How does it work? **

A bucket is determined via the Path+Method+Major in the following way:

    1. Get Hash from Path+Method (we call this route)
    2. Get bucket from Hash+Major (we call this bucketid)

If no hash is known we default to the constant "unlimited" hash. The hash is loaded from HTTP responses using the "X-RateLimit-Bucket" response header.
This hash is per Method+Path and can be stored indefinitely once received.
Some endpoints don't return a hash, this means that the endpoint is **unlimited** and will be in queue with only the major parameter.

To explain this further, lets look at the example of message history. The endpoint to fetch message history is "GET/channels/{channel.id}/messages".
This endpoint does not have any rate limit (unlimited) and will thus use the hash "unlimited+GET/channels/{channel.id}/messages".
The bucket id for this will be "unlimited+GET/channels/{channel.id}/messages:guild_id:{channel.id}:webhook_id" where "{channel.id}" would be replaced with the respective id.
This means you can fetch history concurrently for multiple channels but it will be in sequence for the same channel.

If the endpoint is not unlimited we will receive a hash on the first response.
Once this happens every unlimited bucket will start moving its queue to the correct bucket.
This is done during the queue work iteration so many requests to one endpoint would be moved correctly.

For example, the first message sending:

    public void onReady(ReadyEvent event) {
      TextChannel channel = event.getJDA().getTextChannelById("123");
      for (int i = 1; i <= 100; i++) {
        channel.sendMessage("Message: " + i).queue();
      }
    }

This will send 100 messages on startup. At this point we don't yet know the hash for this route so we put them all in "unlimited+POST/channels/{channel.id}/messages:guild_id:123:webhook_id".
The bucket iterates the requests in sync and gets the first response. This response provides the hash for this route and we create a bucket for it.
Once the response is handled we continue with the next request in the unlimited bucket and notice the new bucket. We then move all related requests to this bucket.

 */
public class BotRateLimiter extends RateLimiter
{
    private static final String RESET_AFTER_HEADER = "X-RateLimit-Reset-After";
    private static final String RESET_HEADER = "X-RateLimit-Reset";
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String GLOBAL_HEADER = "X-RateLimit-Global";
    private static final String HASH_HEADER = "X-RateLimit-Bucket";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String UNLIMITED_BUCKET = "unlimited"; // we generate an unlimited bucket for every major parameter configuration

    private final ReentrantLock bucketLock = new ReentrantLock();
    // Route -> Hash
    private final Map<Route, String> hash = new ConcurrentHashMap<>();
    // Hash + Major Parameter -> Bucket
    private final Map<String, Bucket> bucket = new ConcurrentHashMap<>();
    // Bucket -> Rate-Limit Worker
    private final Map<Bucket, Future<?>> rateLimitQueue = new ConcurrentHashMap<>();
    private Future<?> cleanupWorker;

    public BotRateLimiter(Requester requester)
    {
        super(requester);
    }

    @Override
    public void init()
    {
        cleanupWorker = getScheduler().scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    private ScheduledExecutorService getScheduler()
    {
        return requester.getJDA().getRateLimitPool();
    }

    private void cleanup()
    {
        // This will remove buckets that are no longer needed every 30 seconds to avoid memory leakage
        // We will keep the hashes in memory since they are very limited (by the amount of possible routes)
        MiscUtil.locked(bucketLock, () -> {
            int size = bucket.size();
            Iterator<Map.Entry<String, Bucket>> entries = bucket.entrySet().iterator();

            while (entries.hasNext())
            {
                Map.Entry<String, Bucket> entry = entries.next();
                String key = entry.getKey();
                Bucket bucket = entry.getValue();
                if (bucket.isUnlimited() && bucket.requests.isEmpty())
                    entries.remove(); // remove unlimited if requests are empty
                // If the requests of the bucket are drained and the reset is expired the bucket has no valuable information
                else if (bucket.requests.isEmpty() && bucket.reset <= getNow())
                    entries.remove();
            }
            // Log how many buckets were removed
            size -= bucket.size();
            if (size > 0)
                log.debug("Removed {} expired buckets", size);
        });
    }

    private String getRouteHash(Route route)
    {
        return hash.getOrDefault(route, UNLIMITED_BUCKET + "+" + route);
    }

    @Override
    protected void shutdown()
    {
        super.shutdown();
        if (cleanupWorker != null)
            cleanupWorker.cancel(false);
    }

    @Override
    public Long getRateLimit(Route.CompiledRoute route)
    {
        Bucket bucket = getBucket(route, false);
        return bucket == null ? 0L : bucket.getRateLimit();
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void queueRequest(Request request)
    {
        // Create bucket and enqueue request
        MiscUtil.locked(bucketLock, () -> {
            Bucket bucket = getBucket(request.getRoute(), true);
            bucket.enqueue(request);
            runBucket(bucket);
        });
    }

    @Override
    protected Long handleResponse(Route.CompiledRoute route, okhttp3.Response response)
    {
        bucketLock.lock();
        try
        {
            long rateLimit = updateBucket(route, response).getRateLimit();
            if (response.code() == 429)
                return rateLimit;
            else
                return null;
        }
        finally
        {
            bucketLock.unlock();
        }
    }

    private Bucket updateBucket(Route.CompiledRoute route, okhttp3.Response response)
    {
        return MiscUtil.locked(bucketLock, () -> {
            try
            {
                Bucket bucket = getBucket(route, true);
                Headers headers = response.headers();

                boolean wasUnlimited = bucket.isUnlimited();
                boolean global = headers.get(GLOBAL_HEADER) != null;
                String hash = headers.get(HASH_HEADER);
                long now = getNow();

                // Create a new bucket for the hash if needed
                Route baseRoute = route.getBaseRoute();
                if (hash != null)
                {
                    if (!this.hash.containsKey(baseRoute))
                    {
                        this.hash.put(baseRoute, hash);
                        log.debug("Caching bucket hash {} -> {}", baseRoute, hash);
                    }

                    bucket = getBucket(route, true);
                }

                // Handle global rate limit if necessary
                if (global)
                {
                    String retryAfterHeader = headers.get(RETRY_AFTER_HEADER);
                    long retryAfter = parseLong(retryAfterHeader);
                    requester.getJDA().getSessionController().setGlobalRatelimit(now + retryAfter);
                    log.error("Encountered global rate limit! Retry-After: {} ms", retryAfter);
                }
                // Handle hard rate limit, pretty much just log that it happened
                else if (response.code() == 429)
                {
                    // Update the bucket to the new information
                    String retryAfterHeader = headers.get(RETRY_AFTER_HEADER);
                    long retryAfter = parseLong(retryAfterHeader);
                    bucket.remaining = 0;
                    bucket.reset = getNow() + retryAfter;
                    // don't log warning if we are switching bucket, this means it was an issue with an un-hashed route that is now resolved
                    if (hash == null || !wasUnlimited)
                        log.warn("Encountered 429 on route {} with bucket {} Retry-After: {} ms", baseRoute, bucket.bucketId, retryAfter);
                    else
                        log.debug("Encountered 429 on route {} with bucket {} Retry-After: {} ms", baseRoute, bucket.bucketId, retryAfter);
                    return bucket;
                }

                // If hash is null this means we didn't get enough information to update a bucket
                if (hash == null)
                    return bucket;

                // Update the bucket parameters with new information
                String limitHeader = headers.get(LIMIT_HEADER);
                String remainingHeader = headers.get(REMAINING_HEADER);
                String resetAfterHeader = headers.get(RESET_AFTER_HEADER);
                String resetHeader = headers.get(RESET_HEADER);

                bucket.limit = (int) Math.max(1L, parseLong(limitHeader));
                bucket.remaining = (int) parseLong(remainingHeader);
                if (requester.getJDA().isRelativeRateLimit())
                    bucket.reset = now + parseDouble(resetAfterHeader);
                else
                    bucket.reset = parseDouble(resetHeader);
                log.trace("Updated bucket {} to ({}/{}, {})", bucket.bucketId, bucket.remaining, bucket.limit, bucket.reset - now);
                return bucket;
            }
            catch (Exception e)
            {
                Bucket bucket = getBucket(route, true);
                log.error("Encountered Exception while updating a bucket. Route: {} Bucket: {} Code: {} Headers:\n{}",
                        route.getBaseRoute(), bucket, response.code(), response.headers(), e);
                return bucket;
            }
        });
    }

    @Contract("_,true->!null")
    private Bucket getBucket(Route.CompiledRoute route, boolean create)
    {
        return MiscUtil.locked(bucketLock, () ->
        {
            // Retrieve the hash via the route
            String hash = getRouteHash(route.getBaseRoute());
            // Get or create a bucket for the hash + major parameters
            String bucketId = hash + ":" + route.getMajorParameters();
            Bucket bucket = this.bucket.get(bucketId);
            if (bucket == null && create)
                this.bucket.put(bucketId, bucket = new Bucket(bucketId));

            return bucket;
        });
    }

    private void runBucket(Bucket bucket)
    {
        if (isShutdown)
            return;
        // Schedule a new bucket worker if no worker is running
        MiscUtil.locked(bucketLock, () ->
            rateLimitQueue.computeIfAbsent(bucket,
                (k) -> getScheduler().schedule(bucket, bucket.getRateLimit(), TimeUnit.MILLISECONDS)));
    }

    private long parseLong(String input)
    {
        return input == null ? 0L : Long.parseLong(input);
    }

    private long parseDouble(String input)
    {
        //The header value is using a double to represent milliseconds and seconds:
        // 5.250 this is 5 seconds and 250 milliseconds (5250 milliseconds)
        return input == null ? 0L : (long) (Double.parseDouble(input) * 1000);
    }

    public long getNow()
    {
        return System.currentTimeMillis();
    }

    @SuppressWarnings("rawtypes")
    private class Bucket implements IBucket, Runnable
    {
        private final String bucketId;
        private final Queue<Request> requests = new ConcurrentLinkedQueue<>();

        private long reset = 0;
        private int remaining = 1;
        private int limit = 1;

        public Bucket(String bucketId)
        {
            this.bucketId = bucketId;
        }

        public void enqueue(Request request)
        {
            requests.add(request);
        }

        public long getRateLimit()
        {
            long now = getNow();
            long global = requester.getJDA().getSessionController().getGlobalRatelimit();
            // Global rate limit is more important to handle
            if (global > now)
                return global - now;
            // Check if the bucket reset time has expired
            if (reset <= now)
            {
                // Update the remaining uses to the limit (we don't know better)
                remaining = limit;
                return 0L;
            }

            // If there are remaining requests we don't need to do anything, otherwise return backoff in milliseconds
            return remaining < 1 ? reset - now : 0L;
        }

        public long getReset()
        {
            return reset;
        }

        public int getRemaining()
        {
            return remaining;
        }

        public int getLimit()
        {
            return limit;
        }

        private boolean isUnlimited()
        {
            return bucketId.startsWith("unlimited");
        }

        private void backoff()
        {
            // Schedule backoff if requests are not done
            MiscUtil.locked(bucketLock, () -> {
                rateLimitQueue.remove(this);
                if (!requests.isEmpty())
                    runBucket(this);
            });
        }

        @Override
        public void run()
        {
            log.trace("Bucket {} is running {} requests", bucketId, requests.size());
            Iterator<Request> iterator = requests.iterator();
            while (iterator.hasNext())
            {
                Long rateLimit = getRateLimit();
                if (rateLimit > 0L)
                {
                    // We need to backoff since we ran out of remaining uses or hit the global rate limit
                    log.debug("Backing off {} ms for bucket {}", rateLimit, bucketId);
                    break;
                }

                Request request = iterator.next();
                if (isUnlimited())
                {
                    boolean shouldSkip = MiscUtil.locked(bucketLock, () -> {
                        // Attempt moving request to correct bucket if it has been created
                        Bucket bucket = getBucket(request.getRoute(), true);
                        if (bucket != this)
                        {
                            bucket.enqueue(request);
                            iterator.remove();
                            runBucket(bucket);
                            return true;
                        }
                        return false;
                    });
                    if (shouldSkip) continue;
                }

                if (isSkipped(iterator, request))
                    continue;

                try
                {
                    rateLimit = requester.execute(request);
                    if (rateLimit != null)
                        break; // this means we hit a hard rate limit (429) so the request needs to be retried

                    // The request went through so we can remove it
                    iterator.remove();
                }
                catch (Exception ex)
                {
                    log.error("Encountered exception trying to execute request", ex);
                    break;
                }
            }

            backoff();
        }

        @Override
        public Queue<Request> getRequests()
        {
            return requests;
        }

        @Override
        public String toString()
        {
            return bucketId;
        }
    }
}
