package pascal.taie.analysis.graph.callgraph.llmcallgraph;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.LogManager.getLogger;

public class PythonBridge {

    private static final Logger logger = getLogger(PythonBridge.class);

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
//                RateLimiter limiter = RateLimiter.create(1000); // 每秒1000次[9](@ref)
                while (!Thread.currentThread().isInterrupted()) {
                    String cmd = commandQueue.poll(1, TimeUnit.SECONDS);
                    if (cmd != null) {
//                        limiter.acquire();
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
            sendCommand("REQUEST_DATA");
        }
    }

    private void sendCommand(String cmd) {
        commandQueue.offer(cmd);
    }

    public List<String> runScript(String inputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "/home/byx/anaconda3/envs/llm-prompt-engineering/bin/python", "-u",
                "llmagent/python/script.py",
                "--input=" + inputPath
        );

        Process process = pb.start();

        try {
            // 读取文件内容
            String jsonStr = Files.readString(Paths.get("llmagent/io/valuablemethod.json"));

            Gson gson = new Gson();

            return gson.fromJson(jsonStr,
                    new TypeToken<List<String>>(){}.getType());

        } catch (IOException e) {
            logger.error("error when python data back：{}", e.getMessage());;
        }
        return List.of("err");
    }
}
