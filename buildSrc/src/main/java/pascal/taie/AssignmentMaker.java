/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Makes assignments according to configurations.
 */
public final class AssignmentMaker {

    private final static String CLASS_SUFFIX = ".class";

    /**
     * Root directory of Tai-e.
     */
    private final Path TAI_E_ROOT;

    private final Path OUTPUT_DIR;

    /**
     * path to Tai-e jar.
     */
    private final Path TAI_E;

    private final Path SOURCE_DIR;

    private final Path TEST_SOURCE_DIR;

    private final Path TEST_RESOURCES_DIR;

    /**
     * Directory for rt.jar. This jar is needed by Soot frontend.
     */
    private final Path RT_DIR;

    /**
     * Root directory of all assignment content.
     */
    private final Path ASS_ROOT;

    /**
     * Path to the directory for assignment content.
     */
    private final Path ASS_DIR;

    /**
     * Path to the directory for the generated assignment.
     */
    private final Path TARGET_DIR;

    private final Config config;

    private AssignmentMaker(Project project, String name) {
        // configure the paths
        TAI_E_ROOT = project.getRootDir().toPath();
        OUTPUT_DIR = TAI_E_ROOT.resolve("output");
        TAI_E = TAI_E_ROOT.resolve("build/libs/tai-e-all.jar");
        SOURCE_DIR = TAI_E_ROOT.resolve("src/main/java");
        TEST_SOURCE_DIR = TAI_E_ROOT.resolve("src/test/java");
        TEST_RESOURCES_DIR = TAI_E_ROOT.resolve("src/test/resources");
        RT_DIR = TAI_E_ROOT.resolve("java-benchmarks/JREs/jre1.5");
        ASS_ROOT = TAI_E_ROOT.resolve("assignments");
        ASS_DIR = ASS_ROOT.resolve(name);
        TARGET_DIR = OUTPUT_DIR.resolve(name).resolve("tai-e");
        File target = TARGET_DIR.toFile();
        if (target.exists()) {
            deleteDirectory(TARGET_DIR);
        }
        target.mkdirs();
        config = Config.parseConfig(ASS_DIR.resolve("config.yml").toFile());
    }

    public static void run(Project project) {
        Object args = project.getProperties().get("args");
        if (args == null) {
            System.err.println("""
                > Task :Assignments
                Error arguments 'args'! Examples like '-Pargs=A1' or '-Pargs="A1 A2"'
                """);
        } else {
            for (String arg : args.toString().split(" ")) {
                new AssignmentMaker(project, arg).make();
            }
        }
    }

    /**
     * Deletes a directory and all content in it.
     *
     * @param dir the path of the directory to be deleted.
     */
    private static void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                 .map(Path::toFile)
                 .sorted(Comparator.reverseOrder())
                 .forEach(File::delete);
        } catch (IOException e) {
            System.out.println("Exception %s thrown when deleting %s".formatted(e, dir));
        }
    }

    private void make() {
        System.out.println("Making assignment %s at %s ...".formatted(config.getName(), TARGET_DIR));
        packDependencies();
        copySourceFiles();
        copyIncompleteFiles();
        copyTestClasses();
        copyTestResources();
        copyCommonFiles();
        zipPackage();
    }

    private void packDependencies() {
        Path path = TARGET_DIR.resolve("lib/dependencies.jar");
        File parent = path.getParent().toFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        try (var in = new JarInputStream(new FileInputStream(TAI_E.toFile()));
             var out = new JarOutputStream(new FileOutputStream(path.toFile()))
        ) {
            byte[] byteBuff = new byte[1024];
            for (JarEntry entry; (entry = in.getNextJarEntry()) != null; ) {
                String entryName = entry.getName();
                if (!entryName.endsWith(CLASS_SUFFIX) ||
                        config.shouldIncludeClass(entry.getName())) {
                    out.putNextEntry(entry);
                    for (int bytesRead; (bytesRead = in.read(byteBuff)) != -1; ) {
                        out.write(byteBuff, 0, bytesRead);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to pack dependencies", e);
        }
    }

    private void copySourceFiles() {
        Path target = TARGET_DIR.resolve("src/main/java");
        config.getSourceFiles()
                .forEach(className -> copyFile(SOURCE_DIR, target, className,
                        AssignmentMaker::toSourcePath));
    }

    private void copyIncompleteFiles() {
        Path source = ASS_DIR;
        Path target = TARGET_DIR.resolve("src/main/java");
        config.getOverwrittenFiles()
                .forEach(className -> copyFile(source, target, className,
                        AssignmentMaker::toSourcePath));
    }

    private void copyTestClasses() {
        Path target = TARGET_DIR.resolve("src/test/java");
        config.getTestClasses()
                .forEach(className -> copyFile(TEST_SOURCE_DIR, target, className,
                        AssignmentMaker::toSourcePath));
    }

    private void copyFile(Path source, Path target,
                          String item, Function<String, String> converter) {
        String path = converter.apply(item);
        Path from = source.resolve(path);
        Path to = target.resolve(path);
        File parent = to.getParent().toFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        try {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy " + from + " to " + to, e);
        }
    }

    private void copyFile(Path source, Path target, String item) {
        copyFile(source, target, item, Function.identity());
    }

    /**
     * Converts a class name to corresponding path of source file,
     * e.g., class a.b.C will be converted to a/b/C.java.
     */
    private static String toSourcePath(String className) {
        String[] split = className.split("\\.");
        split[split.length - 1] = split[split.length - 1] + ".java";
        return String.join(File.separator, Arrays.asList(split));
    }

    private void copyTestResources() {
        Path target = TARGET_DIR.resolve("src/test/resources");
        config.getTestResources().forEach(item -> {
            copyFile(TEST_RESOURCES_DIR, target, item);
            // some source files in test resources are just dependencies
            // of other test cases, and thus do no have expected result
            config.getAnalyses().forEach(analysis -> {
                String fileName = toExpectedFileName(item, analysis);
                if (fileName != null) {
                    Path expected = TEST_RESOURCES_DIR.resolve(fileName);
                    if (Files.exists(expected)) {
                        copyFile(TEST_RESOURCES_DIR, target, item,
                                x -> toExpectedFileName(x, analysis));
                    }
                }
            });
        });
    }

    private void zipPackage() {
        Path source = TARGET_DIR.getParent();
        File zipFile = OUTPUT_DIR.resolve(config.getPackageName()).toFile();
        try (ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(zipFile))) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(
                        Path file, BasicFileAttributes attributes) {
                    // only copy files, no symbolic links
                    if (attributes.isSymbolicLink()) {
                        return FileVisitResult.CONTINUE;
                    }
                    try (FileInputStream fis = new FileInputStream(file.toFile())) {
                        Path targetFile = source.relativize(file);
                        zos.putNextEntry(new ZipEntry(targetFile.toString()));

                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                        // if large file, throws out of memory
                        zos.closeEntry();
                        System.out.println("Zip file : " + file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    System.err.println("Unable to zip : " + file);
                    exc.printStackTrace();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static @Nullable
    String toExpectedFileName(String testPath, String analysis) {
        String[] split = testPath.split("/");
        String file = split[split.length - 1];
        if (file.endsWith(".java")) {
            split[split.length - 1] = file.substring(0,
                    file.length() - ".java".length()) +
                    "-" + analysis +
                    "-expected.txt";
            return String.join(File.separator, Arrays.asList(split));
        }
        return null;
    }

    /**
     * Copies common files that are the same across assignments.
     */
    private void copyCommonFiles() {
        // Gradle-related
        copyFile(ASS_ROOT, TARGET_DIR, "build.gradle.kts");
        copyFile(TAI_E_ROOT, TARGET_DIR, "gradlew");
        copyFile(TAI_E_ROOT, TARGET_DIR, "gradlew.bat");
        copyFile(TAI_E_ROOT, TARGET_DIR, "gradle/wrapper/gradle-wrapper.jar");
        copyFile(TAI_E_ROOT, TARGET_DIR, "gradle/wrapper/gradle-wrapper.properties");
        // copyright.txt
        copyFile(ASS_ROOT, TARGET_DIR, "copyright.txt");
        // lib/rt.jar
        copyFile(RT_DIR, TARGET_DIR.resolve("lib"), "rt.jar");
        // Ax/plan.yml
        copyFile(ASS_DIR, TARGET_DIR, "plan.yml");
    }

    /**
     * Configuration for which files should be included in each assignment.
     */
    private static class Config {

        /**
         * Name of this assignment, which should be "Ax" where x is
         * the assignment number.
         */
        private final String name;

        /**
         * List of IDs of analyses to be tested in the assignment.
         */
        private final List<String> analyses;

        /**
         * Name of assignment package.
         */
        private final String packageName;

        /**
         * Classes to be excluded in the assignment.
         */
        private final List<String> exclude;

        /**
         * Source files to be included in the assignment.
         */
        private final List<String> sourceFiles;

        /**
         * Source files to be included in the assignment.
         * These files overwrite the original source files in Tai-e for
         * specific assignment, and should have been prepared in folder Ax/.
         */
        private final List<String> overwrittenFiles;

        /**
         * Test classes to be included in the assignment.
         */
        private final List<String> testClasses;

        /**
         * Test resources (i.e., test-related files) to be included in the assignment.
         */
        private final List<String> testResources;

        @JsonCreator
        private Config(
                @JsonProperty("name") String name,
                @JsonProperty("analyses") List<String> analyses,
                @JsonProperty("packageName") String packageName,
                @JsonProperty("exclude") List<String> exclude,
                @JsonProperty("sourceFiles") List<String> sourceFiles,
                @JsonProperty("overwrittenFiles") List<String> overwrittenFiles,
                @JsonProperty("testClasses") List<String> testClasses,
                @JsonProperty("testResources") List<String> testResources) {
            this.name = name;
            this.analyses = Objects.requireNonNull(analyses);
            this.packageName = Objects.requireNonNull(packageName);
            this.exclude = Objects.requireNonNullElse(exclude, List.of());
            this.sourceFiles = Objects.requireNonNullElse(sourceFiles, List.of());
            this.overwrittenFiles = Objects.requireNonNull(overwrittenFiles);
            this.testClasses = Objects.requireNonNullElse(testClasses, List.of());
            this.testResources = Objects.requireNonNullElse(testResources, List.of());
        }

        private String getName() {
            return name;
        }

        private List<String> getAnalyses() {
            return analyses;
        }

        private String getPackageName() {
            return packageName;
        }

        private List<String> getSourceFiles() {
            return sourceFiles;
        }

        private List<String> getOverwrittenFiles() {
            return overwrittenFiles;
        }

        private List<String> getTestClasses() {
            return testClasses;
        }

        private List<String> getTestResources() {
            return testResources;
        }

        private static Config parseConfig(File configFile) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
                return mapper.readValue(configFile, Config.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read assignment config " + configFile, e);
            }
        }

        private boolean shouldIncludeClass(String item) {
            return Stream.concat(Stream.concat(exclude.stream(),
                                         sourceFiles.stream()),
                                 overwrittenFiles.stream())
                         .noneMatch(entry -> match(item, entry));
        }

        private boolean match(String item, String entry) {
            if (entry.endsWith("*")) { // entry is package pattern
                if (item.length() - CLASS_SUFFIX.length() < entry.length() - 1) {
                    return false;
                }
                return matchFirstN(item, entry, entry.length() - 1);
            } else { // entry is exact class name
                if (item.length() - CLASS_SUFFIX.length() != entry.length()) {
                    return false;
                }
                return matchFirstN(item, entry, entry.length());
            }
        }

        private static boolean matchFirstN(String item, String entry, int n) {
            for (int i = 0; i < n; ++i) {
                if (!match(item.charAt(i), entry.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean match(char ic, char ec) {
            if (ic == '/') {
                return ec == '.';
            } else {
                return ic == ec;
            }
        }
    }
}
