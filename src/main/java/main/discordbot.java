package main;

import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

public class discordbot {
  public static JDA jda;
  public static void main(String[] args) throws LoginException {
    jda = new JDABuilder(AccountType.BOT).setToken("Njg3NDI2NDk4MDUwNzg1MzU1.Xmlw7g.eWr6DcWUDT0u4YOK2nv4TdOFV-0").build();
    jda.getPresence().setStatus(OnlineStatus.ONLINE);
    jda.getPresence().setActivity(Activity.playing("Clash Royale"));
    jda.addEventListener(new GuildMemberJoin());
  }
}
