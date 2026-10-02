package com.zoner.reminder;

import com.zoner.event.ReminderChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationChannelRegistry {

    private final Map<ReminderChannel, NotificationChannel> channels = new EnumMap<>(ReminderChannel.class);

    public NotificationChannelRegistry(List<NotificationChannel> channelList) {
        for (NotificationChannel channel : channelList) {
            channels.put(channel.getChannel(), channel);
        }
    }

    public NotificationChannel getChannel(ReminderChannel type) {
        NotificationChannel channel = channels.get(type);
        if (channel == null) {
            // Default to in-app if unknown
            return channels.get(ReminderChannel.IN_APP);
        }
        return channel;
    }
}
