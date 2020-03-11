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

package net.dv8tion.jda.api.events.guild.member.update;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.OffsetDateTime;

/**
 * Indicates that a {@link net.dv8tion.jda.api.entities.Member Member} updated their {@link net.dv8tion.jda.api.entities.Guild Guild} boost time.
 * <br>This event requires {@link net.dv8tion.jda.api.JDABuilder#setGuildSubscriptionsEnabled(boolean) guild subscriptions}
 * to be enabled.
 * <br>This happens when a member started or stopped boosting a guild.
 *
 * <p>Can be used to retrieve members who boosted, triggering guild.
 *
 * <p>Identifier: {@code boost_time}
 */
public class GuildMemberUpdateBoostTimeEvent extends GenericGuildMemberUpdateEvent<OffsetDateTime>
{
    public static final String IDENTIFIER = "boost_time";

    public GuildMemberUpdateBoostTimeEvent(@Nonnull JDA api, long responseNumber, @Nonnull Member member, @Nullable OffsetDateTime previous)
    {
        super(api, responseNumber, member, previous, member.getTimeBoosted(), IDENTIFIER);
    }

    /**
     * The old boost time
     *
     * @return The old boost time
     */
    @Nullable
    public OffsetDateTime getOldTimeBoosted()
    {
        return getOldValue();
    }

    /**
     * The new boost time
     *
     * @return The new boost time
     */
    @Nullable
    public OffsetDateTime getNewTimeBoosted()
    {
        return getNewValue();
    }
}
