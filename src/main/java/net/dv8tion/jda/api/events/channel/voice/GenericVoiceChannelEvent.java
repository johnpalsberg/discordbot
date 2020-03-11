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
package net.dv8tion.jda.api.events.channel.voice;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.Event;

import javax.annotation.Nonnull;

/**
 * Indicates that a {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel} event was fired.
 * <br>Every VoiceChannelEvent is derived from this event and can be casted.
 *
 * <p>Can be used to detect any VoiceChannelEvent.
 */
public abstract class GenericVoiceChannelEvent extends Event
{
    private final VoiceChannel channel;

    public GenericVoiceChannelEvent(@Nonnull JDA api, long responseNumber, @Nonnull VoiceChannel channel)
    {
        super(api, responseNumber);
        this.channel = channel;
    }

    /**
     * The {@link net.dv8tion.jda.api.entities.VoiceChannel VoiceChannel}
     *
     * @return The VoiceChannel
     */
    @Nonnull
    public VoiceChannel getChannel()
    {
        return channel;
    }

    /**
     * The {@link net.dv8tion.jda.api.entities.Guild Guild}
     * <br>Shortcut for {@code getChannel().getGuild()}
     *
     * @return The Guild
     */
    @Nonnull
    public Guild getGuild()
    {
        return channel.getGuild();
    }
}
