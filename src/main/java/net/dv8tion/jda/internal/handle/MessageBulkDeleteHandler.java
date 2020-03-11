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

package net.dv8tion.jda.internal.handle;

import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;

import java.util.LinkedList;

public class MessageBulkDeleteHandler extends SocketHandler
{
    public MessageBulkDeleteHandler(JDAImpl api)
    {
        super(api);
    }

    @Override
    protected Long handleInternally(DataObject content)
    {
        if (!content.isNull("guild_id"))
        {
            long guildId = content.getLong("guild_id");
            if (getJDA().getGuildSetupController().isLocked(guildId))
                return guildId;
        }
        final long channelId = content.getLong("channel_id");

        if (getJDA().isBulkDeleteSplittingEnabled())
        {
            SocketHandler handler = getJDA().getClient().getHandlers().get("MESSAGE_DELETE");
            content.getArray("ids").forEach(id ->
            {
                handler.handle(responseNumber, DataObject.empty()
                    .put("t", "MESSAGE_DELETE")
                    .put("d", DataObject.empty()
                        .put("channel_id", Long.toUnsignedString(channelId))
                        .put("id", id)));
            });
        }
        else
        {
            TextChannel channel = getJDA().getTextChannelById(channelId);
            if (channel == null)
            {
                getJDA().getEventCache().cache(EventCache.Type.CHANNEL, channelId, responseNumber, allContent, this::handle);
                EventCache.LOG.debug("Received a Bulk Message Delete for a TextChannel that is not yet cached.");
                return null;
            }

            if (getJDA().getGuildSetupController().isLocked(channel.getGuild().getIdLong()))
            {
                return channel.getGuild().getIdLong();
            }

            LinkedList<String> msgIds = new LinkedList<>();
            content.getArray("ids").forEach(id -> msgIds.add((String) id));
            getJDA().handleEvent(
                    new MessageBulkDeleteEvent(
                            getJDA(), responseNumber,
                            channel, msgIds));
        }
        return null;
    }
}
