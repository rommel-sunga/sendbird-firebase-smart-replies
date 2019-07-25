package com.example.smartreplytest;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseSmartReply;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseTextMessage;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestion;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestionResult;
import com.sendbird.android.BaseChannel;
import com.sendbird.android.BaseMessage;
import com.sendbird.android.GroupChannel;
import com.sendbird.android.SendBird;
import com.sendbird.android.SendBirdException;
import com.sendbird.android.User;
import com.sendbird.android.UserMessage;
import com.sendbird.android.UserMessageParams;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // Firebase Conversation Object
    private List<FirebaseTextMessage> conversation;

    // SendBird Pre-created Values
    private String currentUserId = "sendbird_user_1";
    private String currentAppId = "4513ED93-B71C-4056-9FD6-78E44E4AD8C8";
    private String currentChannelId = "firebase_test_channel_1";
    private GroupChannel mGroupChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase Conversation Object will be filled with object information
        conversation = new ArrayList<>();
        conversation.add(FirebaseTextMessage.createForLocalUser("We are just testing", System.currentTimeMillis()));

        SendBird.init(currentAppId, getApplicationContext());

        SendBird.connect(currentUserId, new SendBird.ConnectHandler() {
            @Override
            public void onConnected(User user, SendBirdException e) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }

                // This is assuming it is the case where you are retrieving a old GroupChannel
                // For GroupChannel.createChannel() simply skip retrieving the message history
                GroupChannel.getChannel("firebase_test_channel_1", new GroupChannel.GroupChannelGetHandler() {
                    @Override
                    public void onResult(GroupChannel groupChannel, SendBirdException e) {
                        if (e != null) {
                            e.printStackTrace();
                            return;
                        }

                        mGroupChannel = groupChannel;

                        // When you load previous messages for a channel, add them to the Firebase Conversation object.
                        loadPreviousMessagesAndUpdateConversation(groupChannel);

                    }
                });
            }
        });
    }

    private void loadPreviousMessagesAndUpdateConversation(GroupChannel groupChannel) {
        groupChannel.getPreviousMessagesByTimestamp(System.currentTimeMillis(), true, 20, false, BaseChannel.MessageTypeFilter.USER, null, new BaseChannel.GetMessagesHandler() {
            @Override
            public void onResult(List<BaseMessage> list, SendBirdException e) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }

                for (BaseMessage message : list) {
                    UserMessage userMessage = ((UserMessage) message);
                    String senderUserId = userMessage.getSender().getUserId();
                    if (senderUserId.equals(currentUserId)) {
                        conversation.add(FirebaseTextMessage.createForLocalUser(userMessage.getMessage(), System.currentTimeMillis()));
                    } else {
                        conversation.add(FirebaseTextMessage.createForRemoteUser(userMessage.getMessage(), System.currentTimeMillis(), senderUserId));
                    }
                }

                // Before a user sends a message, query the conversation for a suggestion
                getSuggestionAndSendMessage();
            }
        });
    }

    private void getSuggestionAndSendMessage() {
        final List<String> suggestedReplies = new ArrayList<>();

        FirebaseSmartReply smartReply = FirebaseNaturalLanguage.getInstance().getSmartReply();
        smartReply.suggestReplies(conversation)
                .addOnSuccessListener(new OnSuccessListener<SmartReplySuggestionResult>() {
                    @Override
                    public void onSuccess(SmartReplySuggestionResult result) {
                        if (result.getStatus() == SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE) {
                            // The conversation's language isn't supported, so the
                            // the result doesn't contain any suggestions.
                            Log.d("Debug Smart Replies", "Exception occured while calculating smart replies");
                        } else if (result.getStatus() == SmartReplySuggestionResult.STATUS_SUCCESS) {
                            for (SmartReplySuggestion suggestion : result.getSuggestions()) {
                                String replyText = suggestion.getText();
                                suggestedReplies.add(replyText);
                                Log.d("Debug Smart Replies", "smart replies " + replyText);
                            }

                            // In a real example, display the list of suggested messages to the user and let them choose.
                            // Here we simply choose the first suggested message.
                            String messageText = suggestedReplies.get(0);

                            // Send a UserMessage with the suggested message and update the conversation
                            sendUserMessageAndUpdateConversation(mGroupChannel, messageText);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Task failed with an exception
                        // ...
                        e.printStackTrace();
                    }
                });
    }

    private void sendUserMessageAndUpdateConversation(GroupChannel groupChannel, String suggestedMessage) {
        UserMessageParams params = new UserMessageParams();
        params.setMessage(suggestedMessage);
        groupChannel.sendUserMessage(params, new BaseChannel.SendUserMessageHandler() {
            @Override
            public void onSent(UserMessage userMessage, SendBirdException e) {
                if (e != null) {
                    e.printStackTrace();
                    return;
                }

                // Add successfully sent message as part of the conversation
                conversation.add(FirebaseTextMessage.createForLocalUser(userMessage.getMessage(), System.currentTimeMillis()));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Add new messages as they are received to the Firebase Conversation
        SendBird.addChannelHandler(currentChannelId, new SendBird.ChannelHandler() {
            @Override
            public void onMessageReceived(BaseChannel baseChannel, BaseMessage baseMessage) {
                if (baseChannel.getUrl().equals(currentChannelId) && baseMessage instanceof UserMessage) {
                    UserMessage userMessage = (UserMessage) baseMessage;
                    conversation.add(FirebaseTextMessage.createForRemoteUser(userMessage.getMessage(), System.currentTimeMillis(), userMessage.getSender().getUserId()));
                }
            }
        });
    }
}
