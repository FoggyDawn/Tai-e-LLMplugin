package pascal.taie.analysis.graph.callgraph.llmcallgraph;

import org.apache.logging.log4j.LogManager;
import org.gradle.internal.impldep.com.google.common.util.concurrent.RateLimiter;

import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PythonBridge {
    private static Process pythonProcess;
    private BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();

    // interactive python interface
    public void interactiveLLM(String pp ) throws IOException {
        String pythonPath = pp == null ? "~/anaconda3/envs/llm-prompt-engineering/bin/python" : pp;
        ProcessBuilder pb = new ProcessBuilder(
                pythonPath, "-u",
                "llmagent/python/runner.py"
        );
        pb.redirectErrorStream(true);
        pythonProcess = pb.start();

        // 输出监听线程
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pythonProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleLLMMessage(line);
                }
            } catch (IOException e) {
                LogManager.getLogger().error("Python输出流异常", e);
            }
        }).start();

        // 输入发送线程
        new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(pythonProcess.getOutputStream()))) {
                RateLimiter limiter = RateLimiter.create(1000); // 每秒1000次[9](@ref)
                while (!Thread.currentThread().isInterrupted()) {
                    String cmd = commandQueue.poll(1, TimeUnit.SECONDS);
                    if (cmd != null) {
                        limiter.acquire();
                        writer.write(cmd + "\n");
                        writer.flush();
                    }
                }
            } catch (Exception e) {
                LogManager.getLogger().error("指令发送失败", e);
            }
        }).start();
    }

    private void handleLLMMessage(String msg) {
        // 消息解析逻辑...
        if (msg.startsWith("REQUEST_DATA")) {
            //
        }
    }

    public void sendCommand(String cmd) {
        commandQueue.offer(cmd);
    }

    public String runScript(String inputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "/home/byx/anaconda3/envs/llm-prompt-engineering/bin/python", "-u",
                "llmagent/python/runner.py",
                "--input=" + inputPath
        );

        Process process = pb.start();
        // 实时捕获输出流
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
