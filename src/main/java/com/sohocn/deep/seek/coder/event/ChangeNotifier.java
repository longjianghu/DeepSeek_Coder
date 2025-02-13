package com.sohocn.deep.seek.coder.event;

import com.intellij.util.messages.Topic;

public interface ChangeNotifier {
    Topic<ChangeNotifier> TOPIC = Topic.create("Changed", ChangeNotifier.class);
    
    void changed(ChangeEvent event);
} 