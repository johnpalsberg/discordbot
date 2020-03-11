package main;

import java.util.Random;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildMemberJoin extends ListenerAdapter {
 String[] messages = Messages.getMessages();
 public void onGuildMemeberJoin(GuildMemberJoinEvent event) {
   Random rand = new Random();
   int randNum = rand.nextInt(messages.length);
   EmbedBuilder join = new EmbedBuilder();
   join.setColor(0x66d8ff);
   join.setDescription(messages[randNum].replace("[member]",event.getMember().getAsMention()));
   event.getGuild().getDefaultChannel().sendMessage(join.build());
 }
}
