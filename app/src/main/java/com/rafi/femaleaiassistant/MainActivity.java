package com.rafi.femaleaiassistant;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.text.method.ScrollingMovementMethod;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class MainActivity extends Activity {
    private TextView chatView;
    private EditText inputText, apiKeyText;
    private Button sendButton, talkButton, saveKeyButton;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private SharedPreferences prefs;

    private final String SYSTEM_PROMPT =
            "You are a polite female AI assistant. Speak warmly and helpfully. Keep replies short and friendly.";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ai_assistant", MODE_PRIVATE);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 28);
        root.setBackgroundColor(Color.rgb(255, 246, 250));

        TextView avatar = new TextView(this);
        avatar.setText("👩‍💼");
        avatar.setTextSize(92);
        avatar.setGravity(Gravity.CENTER);
        root.addView(avatar, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Your Female AI Assistant");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(80, 40, 60));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        apiKeyText = new EditText(this);
        apiKeyText.setHint("Paste API key here");
        apiKeyText.setSingleLine(true);
        apiKeyText.setText(prefs.getString("api_key", ""));
        root.addView(apiKeyText, new LinearLayout.LayoutParams(-1, -2));

        saveKeyButton = new Button(this);
        saveKeyButton.setText("Save Key");
        saveKeyButton.setOnClickListener(v -> {
            prefs.edit().putString("api_key", apiKeyText.getText().toString().trim()).apply();
            toast("API key saved");
        });
        root.addView(saveKeyButton);

        chatView = new TextView(this);
        chatView.setText("Assistant: Salaam, I am ready to talk with you.\\n");
        chatView.setTextSize(16);
        chatView.setTextColor(Color.rgb(40, 40, 40));
        chatView.setPadding(20, 20, 20, 20);
        chatView.setBackgroundResource(getResources().getIdentifier("card_bg", "drawable", getPackageName()));
        chatView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(chatView, new LinearLayout.LayoutParams(-1, 0, 1));

        inputText = new EditText(this);
        inputText.setHint("Type your message...");
        root.addView(inputText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        sendButton = new Button(this);
        sendButton.setText("Send");
        sendButton.setOnClickListener(v -> sendMessage(inputText.getText().toString()));

        talkButton = new Button(this);
        talkButton.setText("Talk to Her");
        talkButton.setOnClickListener(v -> startListening());

        buttons.addView(sendButton, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(talkButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);

        setContentView(root);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale preferred = Locale.US;
                tts.setLanguage(preferred);
                tts.setPitch(1.18f);
                tts.setSpeechRate(0.95f);
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { toast("Listening..."); }
            public void onBeginningOfSpeech() {}
            public void onRmsChanged(float rmsdB) {}
            public void onBufferReceived(byte[] buffer) {}
            public void onEndOfSpeech() {}
            public void onError(int error) { toast("Could not hear clearly. Try again."); }
            public void onResults(Bundle results) {
                ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (words != null && !words.isEmpty()) {
                    inputText.setText(words.get(0));
                    sendMessage(words.get(0));
                }
            }
            public void onPartialResults(Bundle partialResults) {}
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
    }

    private void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;
        inputText.setText("");
        append("You: " + message);
        new Thread(() -> {
            String reply = askAI(message.trim());
            runOnUiThread(() -> {
                append("Assistant: " + reply);
                speak(reply);
            });
        }).start();
    }

    private String askAI(String userMessage) {
        String apiKey = prefs.getString("api_key", "");
        if (apiKey.isEmpty()) return "Please paste and save your API key first.";

        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", "gpt-4o-mini");

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
            messages.put(new JSONObject().put("role", "user").put("content", userMessage));
            body.put("messages", messages);

            OutputStream os = con.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            InputStream is = con.getResponseCode() < 400 ? con.getInputStream() : con.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            JSONObject json = new JSONObject(response.toString());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();

        } catch (Exception e) {
            return "Sorry, I could not connect. Check internet or API key.";
        }
    }

    private void append(String text) {
        chatView.append("\\n" + text + "\\n");
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_reply");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
