package com.tharanesh.desktopai;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class AI extends Activity {
    private LinearLayout messages;
    private EditText input;
    private TextView status;
    private boolean terminalMode = false;

    private static final String WORKER_URL =
            "https://desktop-ai-backend.tharaneshmanogaran.workers.dev/";
    // Kept in memory only; never written into source or preferences.
    private String appAccessToken;


    private final java.util.concurrent.ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Pattern blocked = Pattern.compile(
        "(--no-preserve-root|mkfs|/etc/shadow|>\\s*/dev/sd[a-z])",
        Pattern.CASE_INSENSITIVE
    );

    private final Pattern risky = Pattern.compile(
        "\\b(rm|rmdir|shred|sudo|su|chmod|chown|kill|reboot|shutdown|"
      + "mount|umount|dd|apt|pkg|pip|npm)\\b",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 16, 24));
        getWindow().setNavigationBarColor(Color.rgb(16, 16, 24));
        buildUI();
        addMessage("Jarvis", "Hello! I'm Jarvis. The Android interface is ready. "
                + "AI service and the full Linux environment will be connected next.");
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        return b;
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(16, 16, 24));

        TextView title = text("Desktop.AI", 25);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        status = text("JARVIS  •  Ready", 12);
        status.setTextColor(Color.rgb(170, 170, 190));
        root.addView(status);

        LinearLayout modes = new LinearLayout(this);
        Button chat = button("Chat");
        Button terminal = button("Terminal");
        modes.addView(chat, new LinearLayout.LayoutParams(0, dp(48), 1));
        modes.addView(terminal, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(modes);

        ScrollView scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setHint("Message Jarvis...");
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button send = button("Send");
        composer.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(composer);

        chat.setOnClickListener(v -> {
            terminalMode = false;
            input.setHint("Message Jarvis...");
            status.setText("JARVIS  •  Chat mode");
        });

        terminal.setOnClickListener(v -> {
            terminalMode = true;
            input.setHint("Enter a terminal command...");
            status.setText("JARVIS  •  Android shell mode");
            addMessage("System",
                "This is Android's restricted shell, not full Ubuntu. "
              + "Linux userspace integration is not installed yet.");
        });

        send.setOnClickListener(v -> submit());
        setContentView(root);
    }

    private void addMessage(String who, String message) {
        TextView t = text(who + "\n" + message, 15);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackgroundColor(Color.rgb(34, 34, 48));
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(5), 0, dp(5));
        messages.addView(t, lp);
    }

    private void submit() {
        String command = input.getText().toString().trim();
        if (command.isEmpty()) return;
        input.setText("");
        addMessage("You", command);

        if (!terminalMode) {
            askJarvis(command);
            return;
        }

        if (blocked.matcher(command).find()) {
            addMessage("Jarvis", "Blocked by the local safety filter.");
            return;
        }

        if (risky.matcher(command).find()) {
            new AlertDialog.Builder(this)
                .setTitle("Confirm command")
                .setMessage(command + "\n\nRun this command?")
                .setNegativeButton("Cancel", (d, w) ->
                    addMessage("Jarvis", "Command cancelled."))
                .setPositiveButton("Run", (d, w) -> execute(command))
                .show();
        } else {
            execute(command);
        }
    }


    private void askJarvis(String message) {
        if (appAccessToken == null || appAccessToken.trim().isEmpty()) {
            promptForToken(message);
            return;
        }

        status.setText("JARVIS  •  Connecting...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)
                        new URL(WORKER_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty(
                        "X-App-Token", appAccessToken);

                byte[] payload = new JSONObject()
                        .put("message", message)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);

                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                StringBuilder response = new StringBuilder();
                if (stream != null) {
                    try (InputStream in = stream;
                         java.io.BufferedReader reader =
                           new java.io.BufferedReader(
                             new java.io.InputStreamReader(
                               in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (response.length() < 20000) {
                                response.append(line);
                            }
                        }
                    }
                }

                JSONObject json = new JSONObject(response.toString());
                String reply = json.optString("reply", "");
                String error = json.optString(
                        "error", "Request failed (HTTP " + code + ")");

                runOnUiThread(() -> {
                    status.setText("JARVIS  •  Ready");
                    if (code >= 200 && code < 300 && !reply.isEmpty()) {
                        addMessage("Jarvis", reply);
                    } else {
                        addMessage("Jarvis", error);
                        if (code == 401) {
                            appAccessToken = null;
                            addMessage("System",
                                    "Check the access token and try again.");
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("JARVIS  •  Connection error");
                    addMessage("Jarvis",
                            "Couldn't reach the AI backend. Check your "
                          + "internet connection and Worker configuration.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void promptForToken(String message) {
        EditText tokenInput = new EditText(this);
        tokenInput.setSingleLine(true);
        tokenInput.setHint("APP_ACCESS_TOKEN");
        tokenInput.setInputType(
                InputType.TYPE_CLASS_TEXT
              | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Connect to Jarvis")
                .setMessage("Enter your Cloudflare APP_ACCESS_TOKEN. "
                        + "It will remain in memory only until the app "
                        + "process is closed.")
                .setView(tokenInput)
                .setNegativeButton("Cancel", (dialog, which) ->
                        addMessage("Jarvis", "Connection cancelled."))
                .setPositiveButton("Connect", (dialog, which) -> {
                    String token = tokenInput.getText().toString().trim();
                    if (token.isEmpty()) {
                        addMessage("Jarvis", "No token entered.");
                        return;
                    }
                    appAccessToken = token;
                    askJarvis(message);
                })
                .show();
    }

    private void execute(String command) {
        addMessage("Terminal", "$ " + command);
        executor.execute(() -> {
            StringBuilder output = new StringBuilder();
            int code = -1;
            try {
                Process p = new ProcessBuilder("sh", "-c", command)
                        .redirectErrorStream(true).start();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (output.length() < 12000) {
                            output.append(line).append('\n');
                        }
                    }
                }
                code = p.waitFor();
            } catch (Exception e) {
                output.append("Error: ").append(e.getMessage());
            }

            final String result = output.length() == 0
                    ? "(no output)" : output.toString();
            final int exitCode = code;

            runOnUiThread(() -> addMessage("Result (exit " + exitCode + ")",
                    result));
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
