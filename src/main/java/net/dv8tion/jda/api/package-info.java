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

/**
 * The API features of JDA.
 *
 * <p>This package contains information about the JDA version used by this library.
 * See {@link net.dv8tion.jda.api.JDAInfo JDAInfo}!
 *
 * <p>To build a JDA instance you
 * would use the {@link net.dv8tion.jda.api.JDABuilder JDABuilder} with a specified {@link net.dv8tion.jda.api.AccountType AccountType}
 * to differ between a bot and a client account. Client accounts are commonly referenced as User accounts due to them being used by
 * the official Discord Client.
 * <br>It is recommended to <b>not automate</b> accounts from AccountType {@link net.dv8tion.jda.api.AccountType#CLIENT CLIENT}!
 *
 * <p>In addition this package included helpful builders for message sending
 * such as:
 * <ul>
 *     <li>{@link net.dv8tion.jda.api.MessageBuilder MessageBuilder}
 *     <br>Used to build a {@link net.dv8tion.jda.api.entities.Message Message} which can be used to
 *         send a message to a {@link net.dv8tion.jda.api.entities.MessageChannel MessageChannel} together with
 *         an {@link net.dv8tion.jda.api.entities.MessageEmbed MessageEmbed} and Text-To-Speech!</li>
 *
 *     <li>{@link net.dv8tion.jda.api.EmbedBuilder EmbedBuilder}
 *     <br>Used to build a {@link net.dv8tion.jda.api.entities.MessageEmbed MessageEmbed}
 *         which can then be used in the message sending process (see above)</li>
 * </ul>
 */
package net.dv8tion.jda.api;
