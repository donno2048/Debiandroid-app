package dev.debiandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.system.Os;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class MainActivity extends Activity {
    private static final String MARKER = ".installed";
    private TerminalSession session;
    private TerminalView terminal;
    private WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        terminal = new TerminalView(this, null);
        terminal.setTextSize(30);
        terminal.setFocusableInTouchMode(true);
        Client client = new Client();
        terminal.setTerminalViewClient(client);
        terminal.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                terminal.post(() -> {
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT);
                });
            }
            return false;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(terminal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, 0, 0, insets.getInsets(WindowInsets.Type.ime()).bottom);
            return insets;
        });
        root.requestApplyInsets();

        Executors.newSingleThreadExecutor().execute(() -> {
            File rootfs = new File(getFilesDir(), "rootfs");
            File tmp = new File(rootfs, "tmp");
            File home = new File(rootfs, "root");
            File marker = new File(rootfs, MARKER);
            if (!marker.exists()) {
                rootfs.mkdirs();
                tmp.mkdirs();
                home.mkdirs();
                try (TarArchiveInputStream tar = new TarArchiveInputStream(getAssets().open("debian-sid.tar"))) {
                    TarArchiveEntry e;
                    while ((e = tar.getNextEntry()) != null) {
                        String n = e.getName();
                        while (n.startsWith("./")) n = n.substring(2);
                        if (n.isEmpty() || n.startsWith("/") || n.contains("../")) continue;
                        File out = new File(rootfs, n);
                        if (e.isDirectory()) {
                            out.mkdirs();
                        } else if (e.isSymbolicLink()) {
                            out.getParentFile().mkdirs();
                            Files.createSymbolicLink(out.toPath(), Path.of(e.getLinkName()));
                        } else if (e.isFile()) {
                            out.getParentFile().mkdirs();
                            try (FileOutputStream fos = new FileOutputStream(out)) {
                                tar.transferTo(fos);
                            }
                            Os.chmod(out.getAbsolutePath(), e.getMode() & 0777);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to install rootfs", e);
                }
                File bashrc = new File(home, ".bashrc");
                try (OutputStream out = new FileOutputStream(bashrc)) {
                    out.write(("PS1='\\[\\e[01;32m\\]\\u\\[\\e[00m\\]@\\[\\e[01;34m\\]\\w\\[\\e[00m\\]$ '\n" +
                               "shopt -s histappend\n" +
                               "shopt -s checkwinsize\n" +
                               "shopt -s globstar\n" +
                               "alias ls='ls --color=auto --group-directories-first'\n" +
                               "alias grep='grep --color=auto'\n" +
                               "alias dir='dir --color=auto'\n" +
                               "alias diff='diff --color=auto'\n" +
                               "alias sudo=\n").getBytes("UTF-8"));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create .bashrc", e);
                }
                File hosts = new File(rootfs, "etc/hosts");
                if (!hosts.exists()) {
                    try (OutputStream out = new FileOutputStream(hosts)) {
                        out.write("127.0.0.1 localhost\n::1 localhost\n".getBytes("UTF-8"));
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create /etc/hosts", e);
                    }
                }
                try {marker.createNewFile();} catch (Exception e) {}
            }

            String[] args = {
                    "-l", "-0",
                    "-b", tmp.getAbsolutePath() + ":/dev/shm",
                    "-b", "/dev",
                    "-b", "/proc",
                    "-r", rootfs.getAbsolutePath(),
                    "-w", "/root",
                    "/bin/bash",
                    "--rcfile", "/root/.bashrc"
            };

            String[] env = {
                    "HOME=/root",
                    "TMPDIR=/tmp",
                    "LC_ALL=C.UTF-8",
                    "TERM=xterm-256color",
                    "DEBIAN_FRONTEND=noninteractive",
                    "PROOT_TMP_DIR=" + tmp.getAbsolutePath(),
                    "PROOT_LOADER=" + new File(getApplicationInfo().nativeLibraryDir, "libproot-loader.so").getAbsolutePath(),
                    "LD_LIBRARY_PATH=" + getApplicationInfo().nativeLibraryDir,
                    "PREFIX=" + rootfs.getAbsolutePath() + "/usr",
                    "PATH=/usr/lib:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            };

            runOnUiThread(() -> {
                wakeLock = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Debiandroid:WakeLock");
                session = new TerminalSession(
                                new File(getApplicationInfo().nativeLibraryDir, "libproot.so").getAbsolutePath(),
                                getFilesDir().getAbsolutePath(),
                                args, env, 2000, client);
                terminal.attachSession(session);
                terminal.requestFocus();
                wakeLock.acquire();
            });
        });
    }

    @Override
    protected void onDestroy() {
        if (session != null) session.finishIfRunning();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    private final class Client implements TerminalSessionClient, TerminalViewClient {
        @Override public void onTextChanged(TerminalSession s) {
            runOnUiThread(() -> terminal.invalidate());
        }
        @Override public void onTitleChanged(TerminalSession s) {}
        @Override public void onSessionFinished(TerminalSession s) {}
        @Override public void onCopyTextToClipboard(TerminalSession s, String text) {
            ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("terminal", text));
        }
        @Override public void onPasteTextFromClipboard(TerminalSession s) {}
        @Override public void onBell(TerminalSession s) {}
        @Override public void onColorsChanged(TerminalSession s) {}
        @Override public void onTerminalCursorStateChange(boolean state) {}
        @Override public Integer getTerminalCursorStyle() { return null; }
        @Override public void logError(String tag, String message) { android.util.Log.e(tag, message); }
        @Override public void logWarn(String tag, String message) { android.util.Log.w(tag, message); }
        @Override public void logInfo(String tag, String message) { android.util.Log.i(tag, message); }
        @Override public void logDebug(String tag, String message) { android.util.Log.d(tag, message); }
        @Override public void logVerbose(String tag, String message) { android.util.Log.v(tag, message); }
        @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { android.util.Log.e(tag, message, e); }
        @Override public void logStackTrace(String tag, Exception e) { android.util.Log.e(tag, "", e); }
        @Override public float onScale(float scale) { return scale; }
        @Override public void onSingleTapUp(MotionEvent e) {}
        @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
        @Override public boolean shouldEnforceCharBasedInput() { return false; }
        @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
        @Override public boolean isTerminalViewSelected() { return true; }
        @Override public void copyModeChanged(boolean copyMode) {}
        @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
        @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
        @Override public boolean onLongPress(MotionEvent e) { return false; }
        @Override public boolean readControlKey() { return false; }
        @Override public boolean readAltKey() { return false; }
        @Override public boolean readShiftKey() { return false; }
        @Override public boolean readFnKey() { return false; }
        @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
        @Override public void onEmulatorSet() {}
    }
}
