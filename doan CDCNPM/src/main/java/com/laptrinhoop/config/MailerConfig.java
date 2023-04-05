package com.laptrinhoop.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

@Configuration
@PropertySource("classpath:mail.properties")
public class MailerConfig {
	@Autowired
	Environment env;
	
	@Bean
	public JavaMailSender getJavaMailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost("smtp.gmail.com");
		sender.setPort(587);
		sender.setUsername(env.getProperty("mail.username"));
		sender.setPassword(env.getProperty("mail.password"));

		Properties props = sender.getJavaMailProperties();
		props.setProperty("mail.smtp.host", "smtp.gmail.com"); //SMTP Host
		props.setProperty("mail.smtp.port", "587"); //TLS Port
		props.setProperty("mail.smtp.auth", "true"); //enable authentication
		props.setProperty("mail.smtp.starttls.enable", "true"); //enable STARTTLS

		//create Authenticator object to pass in Session.getInstance argument
		Authenticator auth = new Authenticator() {
			//override the getPasswordAuthentication method
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(env.getProperty("mail.username"), env.getProperty("mail.password"));
			}
		};

		return sender;
	}
}
