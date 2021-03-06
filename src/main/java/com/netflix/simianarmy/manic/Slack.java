/**
 * 
 */
package com.netflix.simianarmy.manic;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.netflix.simianarmy.Monkey;
import com.netflix.simianarmy.client.gcloud.Definitions;
import com.netflix.simianarmy.manic.ManicEvent.Command;
import com.netflix.simianarmy.manic.ManicEvent.InstancePayload;
import com.netflix.simianarmy.manic.ManicEvent.SystemPayload;

import console.mw.monitor.slack.AttachmentField;
import console.mw.monitor.slack.MessageAttachment;
import console.mw.monitor.slack.SlackMessage;
import console.mw.monitor.slack.SlackMonitorListener;

/**
 * @author dxiong
 * 
 */
public class Slack implements EventListener {

	private static SlackMonitorListener slack = null;

	private String url;
	private String channel;
	private String agent;

	private ManicChaosMonkey monkey;

	public Slack(ManicChaosMonkey monkey) {
		this.monkey = monkey;
		this.url = monkey.context().configuration().getStr(Definitions.Notifier.SLACK.URL);
		this.channel = monkey.context().configuration().getStrOrElse(Definitions.Notifier.SLACK.CHANNEL,
				Definitions.Notifier.SLACK.CHANNEL_DEFAULT);
		this.agent = monkey.context().configuration().getStrOrElse(Definitions.Notifier.SLACK.AGENT,
				Definitions.Notifier.SLACK.AGENT_DEFAULT);

		MonkeyEventDispatcher.INSTANCE.subscribe(this);
	}

	public synchronized void sendToSlack(SlackMessage message) {
		if (slack == null) {
			try {
				slack = new SlackMonitorListener(new URL(this.url), this.channel, this.agent);
			} catch (MalformedURLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		if (slack != null) {
			slack.sendMessage(message);
		}
	}

	@Override
	public void onEvent(ManicEvent evt) {
		switch (evt.getType()) {
		case MONKEY:
			this.sendToSlack(this.monkeyEvent2Message(evt));
			break;
		case INSTANCE:
			this.sendToSlack(this.instanceEvent2Message(evt));
			break;
		case SYSTEM:
			this.sendToSlack(this.systemEvent2Message(evt));
			break;
		}
	}

	private SlackMessage monkeyEvent2Message(ManicEvent evt) {
		SlackMessage message = new SlackMessage();

		switch (evt.getCommand()) {
		case START:
			message.setText("Monkey is ready");
			break;
		case STOP:
			message.setText("Monkey has been terminated");
			break;
		case PAUSE:
			message.setText("Monkey has been paused");
			break;
		case RESUME:
			message.setText("Monkey has been resumed");
			break;
		case CONFIG:
			message.setText("Monkey has been configured");
			break;
		default:
			message.setText(evt.getCommand().toString());
		}

		List<MessageAttachment> attachments = new ArrayList<MessageAttachment>();
		MessageAttachment attachment = new MessageAttachment();

		List<AttachmentField> fields = new ArrayList<AttachmentField>();
		AttachmentField field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("Version");
		field.setValue(com.netflix.simianarmy.manic.Definitions.VERSION);
		fields.add(field);

		field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("Enabled");
		field.setValue((monkey == null || monkey.isPaused()) ? "false" : "true");

		fields.add(field);
		
		field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("workHours");
		
		String workingHours = String.format("%d-%d %s", monkey.getMonkeyCalendar().openHour(),monkey.getMonkeyCalendar().closeHour(),monkey.getMonkeyCalendar().timezone());
		field.setValue(workingHours);

		fields.add(field);
		

		field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("Project");
		field.setValue(monkey.context().configuration().getStrOrElse(Definitions.GCloud.PROJECT, "UNKNOWN"));

		fields.add(field);

		field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("Zone");
		field.setValue(monkey.context().configuration().getStrOrElse(Definitions.GCloud.ZONE,
				Definitions.GCloud.ZONE_DEFAULT));

		fields.add(field);

		field = new AttachmentField();
		field.setShortText(true);
		field.setTitle("Schedule");
		Monkey.Context context = (Monkey.Context) monkey.context();
		String schedule = context.scheduler().frequency()+" "+ context.scheduler().frequencyUnit();
		field.setValue(schedule);

		fields.add(field);

		attachment.setFields(fields);
		attachments.add(attachment);
		message.setAttachments(attachments);

		return message;
	}

	private SlackMessage instanceEvent2Message(ManicEvent evt) {
		SlackMessage message = new SlackMessage();

		InstancePayload payload = (InstancePayload) evt.getPayload();

		String name = String.format("%s(%s)", payload.getName(), payload.getRegion());
		if (evt.getCommand() == Command.STATUS_UPDATE) {
			message.setText(String.format("%s changed status from %s to %s", name, payload.getPreviousStatus(),
					payload.getStatus()));
		} else {
			message.setText(String.format("%s %s", evt.getCommand(), name));
		}
		return message;
	}

	private SlackMessage systemEvent2Message(ManicEvent evt) {
		SlackMessage message = new SlackMessage();

		SystemPayload payload = (SystemPayload) evt.getPayload();
		message.setText(payload.getMessage());
		return message;
	}

}
