/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.graph.callgraph.llmcallgraph;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
    public void interactiveLLM(String pp) throws IOException {
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

    public void runSingleLLMQuery(String ir, String inputPath, String outputPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "/home/byx/anaconda3/envs/llm-prompt-engineering/bin/python", "-u",
                "llmagent/python/script.py",
                "--ir=" + ir,
                "--input=" + inputPath,
                "--output=" + outputPath
        );

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                logger.error(errorLine); // 记录错误日志
            }

            String jsonStr = reader.lines().collect(Collectors.joining("\n"));
            Gson gson = new Gson();


        }
    }

    public List<String> runScript(String inputPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "/home/byx/anaconda3/envs/llm-prompt-engineering/bin/python", "-u",
                "llmagent/python/script.py",
                "--input=" + inputPath
        );

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()));
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                logger.error(errorLine); // 记录错误日志
            }

            String jsonStr = reader.lines().collect(Collectors.joining("\n"));
            Gson gson = new Gson();

            return gson.fromJson(jsonStr,
                    new TypeToken<List<String>>(){}.getType());
        }

        // file method, slow but stable
//        int exitCode = process.waitFor();
//
//        if (exitCode != 0) {
//            return List.of("Python script error");
//        }
//
//        try {
//            // 读取文件内容
//            String jsonStr = Files.readString(Paths.get("llmagent/io/valuablemethod.json"));
//
//            logger.info(jsonStr);
//
//            Gson gson = new Gson();
//
//            return gson.fromJson(jsonStr,
//                    new TypeToken<List<String>>(){}.getType());
//
//        } catch (IOException e) {
//            logger.error("error when python data back：{}", e.getMessage());
//            return List.of();
//        }
    }
}
