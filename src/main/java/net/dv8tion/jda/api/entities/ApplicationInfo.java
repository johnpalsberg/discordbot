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

package net.dv8tion.jda.api.entities;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

/**
 * Represents a Discord Application from its bot's point of view.
 * 
 * @since  3.0
 * @author Aljoscha Grebe
 * 
 * @see    net.dv8tion.jda.api.JDA#retrieveApplicationInfo()
 */
public interface ApplicationInfo extends ISnowflake
{
    /**
     * Whether the bot requires code grant to invite or not. 
     * 
     * <p>This means that additional OAuth2 steps are required to authorize the application to make a bot join a guild 
     * like {@code &response_type=code} together with a valid {@code &redirect_uri}. 
     * <br>For more information look at the <a href="https://discordapp.com/developers/docs/topics/oauth2">Discord OAuth2 documentation</a>.  
     * 
     * @return Whether the bot requires code grant
     */
    boolean doesBotRequireCodeGrant();

    /**
     * The description of the bot's application.
     * 
     * @return The description of the bot's application or an empty {@link String} if no description is defined
     */
    @Nonnull
    String getDescription();

    /**
     * The icon id of the bot's application.
     * <br>The application icon is <b>not</b> necessarily the same as the bot's avatar!
     * 
     * @return The icon id of the bot's application or null if no icon is defined
     */
    @Nullable
    String getIconId();

    /**
     * The icon-url of the bot's application.
     * <br>The application icon is <b>not</b> necessarily the same as the bot's avatar!
     * 
     * @return The icon-url of the bot's application or null if no icon is defined
     */
    @Nullable
    String getIconUrl();

    /**
     * The team information for this application.
     *
     * @return The {@link net.dv8tion.jda.api.entities.ApplicationTeam}, or null if this application is not in a team.
     */
    @Nullable
    ApplicationTeam getTeam();

    /**
     * Creates a OAuth invite-link used to invite the bot.
     * 
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS}
     * <br>Unnecessary query parameters are stripped.
     *
     * @param  permissions
     *         Possibly empty {@link java.util.Collection Collection} of {@link net.dv8tion.jda.api.Permission Permissions}
     *         that should be requested via invite.
     * 
     * @return The link used to invite the bot
     */
    @Nonnull
    default String getInviteUrl(@Nullable Collection<Permission> permissions)
    {
        return getInviteUrl(null, permissions);
    }

    /**
     * Creates a OAuth invite-link used to invite the bot.
     * 
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS}
     * <br>Unnecessary query parameters are stripped.
     * 
     * @param  permissions
     *         {@link net.dv8tion.jda.api.Permission Permissions} that should be requested via invite.
     * 
     * @return The link used to invite the bot
     */
    @Nonnull
    default String getInviteUrl(@Nullable Permission... permissions)
    {
        return getInviteUrl(null, permissions);
    }

    /**
     * Creates a OAuth invite-link used to invite the bot.
     * 
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS&guild_id=GUILD_ID}
     * <br>Unnecessary query parameters are stripped.
     * 
     * @param  guildId
     *         The id of the pre-selected guild.
     * @param  permissions
     *         Possibly empty {@link java.util.Collection Collection} of {@link net.dv8tion.jda.api.Permission Permissions}
     *         that should be requested via invite.
     *
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     * 
     * @return The link used to invite the bot
     */
    @Nonnull
    String getInviteUrl(@Nullable String guildId, @Nullable Collection<Permission> permissions);

    /**
     * Creates a OAuth invite-link used to invite the bot.
     *
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS&guild_id=GUILD_ID}
     * <br>Unnecessary query parameters are stripped.
     *
     * @param  guildId
     *         The id of the pre-selected guild.
     * @param  permissions
     *         Possibly empty {@link java.util.Collection Collection} of {@link net.dv8tion.jda.api.Permission Permissions}
     *         that should be requested via invite.
     *
     * @return The link used to invite the bot
     */
    @Nonnull
    default String getInviteUrl(long guildId, @Nullable Collection<Permission> permissions)
    {
        return getInviteUrl(Long.toUnsignedString(guildId), permissions);
    }

    /**
     * Creates a OAuth invite-link used to invite the bot.
     * 
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS&guild_id=GUILD_ID}
     * <br>Unnecessary query parameters are stripped.
     * 
     * @param  guildId 
     *         The id of the pre-selected guild.
     * @param  permissions 
     *         Possibly empty array of {@link net.dv8tion.jda.api.Permission Permissions}
     *         that should be requested via invite.
     *
     * @throws java.lang.NumberFormatException
     *         If the provided {@code id} cannot be parsed by {@link Long#parseLong(String)}
     * 
     * @return The link used to invite the bot
     */
    @Nonnull
    default String getInviteUrl(@Nullable String guildId, @Nullable Permission... permissions)
    {
        return getInviteUrl(guildId, permissions == null ? null : Arrays.asList(permissions));
    }

    /**
     * Creates a OAuth invite-link used to invite the bot.
     *
     * <p>The link is provided in the following format:
     * <br>{@code https://discordapp.com/oauth2/authorize?client_id=APPLICATION_ID&scope=bot&permissions=PERMISSIONS&guild_id=GUILD_ID}
     * <br>Unnecessary query parameters are stripped.
     *
     * @param  guildId
     *         The id of the pre-selected guild.
     * @param  permissions
     *         Possibly empty array of {@link net.dv8tion.jda.api.Permission Permissions}
     *         that should be requested via invite.
     *
     * @return The link used to invite the bot
     */
    @Nonnull
    default String getInviteUrl(long guildId, @Nullable Permission... permissions)
    {
        return getInviteUrl(Long.toUnsignedString(guildId), permissions);
    }

    /**
     * The {@link net.dv8tion.jda.api.JDA JDA} instance of this ApplicationInfo
     * (the one logged into this application's bot account).
     * 
     * @return The JDA instance of this ApplicationInfo
     */
    @Nonnull
    JDA getJDA();

    /**
     * The name of the bot's application.
     * <br>The application name is <b>not</b> necessarily the same as the bot's name!
     * 
     * @return The name of the bot's application.
     */
    @Nonnull
    String getName();

    /**
     * The owner of the bot's application. This may be a fake user.
     * 
     * @return The owner of the bot's application
     */
    @Nonnull
    User getOwner();

    /**
     * Whether the bot is public or not. 
     * Public bots can be added by anyone. When false only the owner can invite the bot to guilds.
     * 
     * @return Whether the bot is public
     */
    boolean isBotPublic();
}
