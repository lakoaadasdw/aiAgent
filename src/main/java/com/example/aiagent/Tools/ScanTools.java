package com.example.aiagent.Tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 扫描工具类
 * 提供文件系统扫描、目录扫描、包扫描、注解扫描等多种扫描方法
 */
@Component
public class ScanTools {

    // ==================== 1. 文件系统扫描 ====================

    /**
     * 扫描指定目录下的所有文件（递归）
     *
     * @param dirPath 目录路径
     * @return 文件列表
     */
    @Tool(description = "扫描指定目录下的所有文件（递归），返回文件列表")
    public static List<File> scanFilesRecursively(String dirPath) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }
        scanFilesRecursive(dir, result);
        return result;
    }

    /**
     * 递归扫描指定目录下的所有文件
     *
     * @param dir      目录
     * @param result   结果列表
     */
    private static void scanFilesRecursive(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            result.add(file);
            if (file.isDirectory()) {
                scanFilesRecursive(file, result);
            }
        }
    }

    /**
     * 扫描指定目录下所有文件（非递归，仅当前层）
     *
     * @param dirPath 目录路径
     * @return 文件列表
     */
    @Tool(description = "扫描指定目录下所有文件（非递归，仅当前层），返回文件列表")
    public static List<File> scanFilesFlat(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        File[] files = dir.listFiles();
        return files == null ? Collections.emptyList() : Arrays.asList(files);
    }

    /**
     * 扫描指定目录下所有文件，仅返回普通文件（非目录）
     *
     * @param dirPath 目录路径
     * @return 文件列表
     */
    @Tool(description = "扫描指定目录下所有文件，仅返回普通文件（非目录），递归扫描")
    public static List<File> scanOnlyFiles(String dirPath) {
        return scanFilesRecursively(dirPath).stream()
                .filter(File::isFile)
                .collect(Collectors.toList());
    }

    /**
     * 扫描指定目录下所有子目录
     *
     * @param dirPath 目录路径
     * @return 目录列表
     */
    @Tool(description = "扫描指定目录下所有子目录，递归扫描")
    public static List<File> scanDirectories(String dirPath) {
        return scanFilesRecursively(dirPath).stream()
                .filter(File::isDirectory)
                .collect(Collectors.toList());
    }

    // ==================== 2. 扩展名/文件名过滤扫描 ====================

    /**
     * 按扩展名过滤扫描文件（递归）
     *
     * @param dirPath    目录路径
     * @param extensions 扩展名列表，如 ["java", "xml", "properties"]
     * @return 匹配的文件列表
     */
    @Tool(description = "按扩展名过滤扫描文件（递归），如 extensions=['java','xml']")
    public static List<File> scanFilesByExtensions(String dirPath, String... extensions) {
        Set<String> extSet = Arrays.stream(extensions)
                .map(e -> e.startsWith(".") ? e.toLowerCase() : "." + e.toLowerCase())
                .collect(Collectors.toSet());
        return scanFilesRecursively(dirPath).stream()
                .filter(File::isFile)
                .filter(f -> {
                    String name = f.getName().toLowerCase();
                    return extSet.stream().anyMatch(name::endsWith);
                })
                .collect(Collectors.toList());
    }

    /**
     * 按文件名关键词过滤扫描文件（递归）
     *
     * @param dirPath 目录路径
     * @param keyword 文件名关键词
     * @return 匹配的文件列表
     */
    @Tool(description = "按文件名关键词过滤扫描文件（递归），查找文件名中包含指定关键词的文件")
    public static List<File> scanFilesByNameKeyword(String dirPath, String keyword) {
        return scanFilesRecursively(dirPath).stream()
                .filter(File::isFile)
                .filter(f -> f.getName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * 按文件大小范围过滤扫描文件（递归）
     *
     * @param dirPath 目录路径
     * @param minSize 最小字节数（含），-1 表示不限制
     * @param maxSize 最大字节数（含），-1 表示不限制
     * @return 匹配的文件列表
     */
    @Tool(description = "按文件大小范围过滤扫描文件（递归），minSize=-1表示不限制下限，maxSize=-1表示不限制上限")
    public static List<File> scanFilesBySize(String dirPath, long minSize, long maxSize) {
        return scanFilesRecursively(dirPath).stream()
                .filter(File::isFile)
                .filter(f -> {
                    long len = f.length();
                    boolean passMin = minSize < 0 || len >= minSize;
                    boolean passMax = maxSize < 0 || len <= maxSize;
                    return passMin && passMax;
                })
                .collect(Collectors.toList());
    }

    // ==================== 3. 包扫描（Class扫描） ====================

    /**
     * 扫描指定包下的所有类名（基于类路径）
     *
     * @param packageName 包名，如 "com.example"
     * @return 类全限定名列表
     */
    @Tool(description = "扫描指定包下的所有类名（基于类路径），返回类全限定名列表，如 packageName='com.example'")
    public static List<String> scanClassesInPackage(String packageName) {
        List<String> classNames = new ArrayList<>();
        String path = packageName.replace('.', '/');
        try {
            Enumeration<java.net.URL> resources = Thread.currentThread()
                    .getContextClassLoader().getResources(path);
            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                if (resource.getProtocol().equals("file")) {
                    File dir = new File(resource.toURI());
                    if (dir.isDirectory()) {
                        scanClassesInDir(dir, packageName, classNames);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("扫描包失败: " + packageName + " - " + e.getMessage());
        }
        return classNames;
    }

    /**
     * 递归扫描目录中的class文件
     *
     * @param dir        目录
     * @param packageName 包名
     * @param classNames  结果列表
     */
    private static void scanClassesInDir(File dir, String packageName, List<String> classNames) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanClassesInDir(file, packageName + "." + file.getName(), classNames);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    /**
     * 扫描指定包下的所有类（并尝试加载Class对象）
     *
     * @param packageName 包名
     * @return Class对象列表（仅成功加载的）
     */
    @Tool(description = "扫描指定包下的所有类并加载为Class对象，返回类全限定名列表，如 packageName='com.example'")
    public static List<Class<?>> scanClasses(String packageName) {
        return scanClassesInPackage(packageName).stream()
                .map(name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 4. 注解扫描 ====================

    /**
     * 扫描指定包下带有特定注解的类
     *
     * @param packageName  包名
     * @param annotation   注解Class
     * @param <A>          注解类型
     * @return 带有该注解的类列表
     */
    @SuppressWarnings("unchecked")
    @Tool(description = "扫描指定包下带有特定注解的类，返回类全限定名列表，如 packageName='com.example', annotation=org.springframework.stereotype.Service")
    public static <A extends java.lang.annotation.Annotation> List<Class<?>> scanAnnotatedClasses(
            String packageName, Class<A> annotation) {

        return scanClasses(packageName).stream()
                .filter(clazz -> clazz.isAnnotationPresent(annotation))
                .collect(Collectors.toList());
    }

    /**
     * 扫描指定类中带有特定注解的方法
     *
     * @param clazz      目标类
     * @param annotation 注解Class
     * @param <A>        注解类型
     * @return 带有该注解的方法列表
     */
    @SuppressWarnings("unchecked")
    @Tool(description = "扫描指定类中带有特定注解的方法，返回方法名列表")
    public static <A extends java.lang.annotation.Annotation> List<java.lang.reflect.Method> scanAnnotatedMethods(
            Class<?> clazz, Class<A> annotation) {

        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotation))
                .collect(Collectors.toList());
    }

    /**
     * 扫描指定包下所有类中带有特定注解的方法
     *
     * @param packageName 包名
     * @param annotation  注解Class
     * @param <A>         注解类型
     * @return Map：类 -> 该类的注解方法列表
     */
    @SuppressWarnings("unchecked")
    @Tool(description = "扫描指定包下所有类中带有特定注解的方法，返回 类名->方法列表 的映射")
    public static <A extends java.lang.annotation.Annotation> Map<Class<?>, List<java.lang.reflect.Method>> scanAnnotatedMethodsInPackage(
            String packageName, Class<A> annotation) {

        Map<Class<?>, List<java.lang.reflect.Method>> result = new LinkedHashMap<>();
        List<Class<?>> classes = scanClasses(packageName);
        for (Class<?> clazz : classes) {
            List<java.lang.reflect.Method> methods = scanAnnotatedMethods(clazz, annotation);
            if (!methods.isEmpty()) {
                result.put(clazz, methods);
            }
        }
        return result;
    }

    // ==================== 5. 接口/父类扫描 ====================

    /**
     * 扫描指定包下实现了特定接口的所有类
     *
     * @param packageName   包名
     * @param interfaceClzz 接口Class
     * @return 实现该接口的类列表
     */
    @Tool(description = "扫描指定包下实现了特定接口的所有类，返回类全限定名列表")
    public static List<Class<?>> scanClassesImplementing(String packageName, Class<?> interfaceClzz) {
        return scanClasses(packageName).stream()
                .filter(clazz -> !clazz.equals(interfaceClzz))
                .filter(interfaceClzz::isAssignableFrom)
                .collect(Collectors.toList());
    }

    /**
     * 扫描指定包下继承自特定父类的所有类
     *
     * @param packageName 包名
     * @param superClzz   父类Class
     * @return 子类列表
     */
    @Tool(description = "扫描指定包下继承自特定父类的所有类，返回类全限定名列表")
    public static List<Class<?>> scanClassesExtending(String packageName, Class<?> superClzz) {
        return scanClasses(packageName).stream()
                .filter(clazz -> !clazz.equals(superClzz))
                .filter(clazz -> superClzz.isAssignableFrom(clazz))
                .collect(Collectors.toList());
    }

    // ==================== 6. 内容扫描（文本搜索） ====================

    /**
     * 在指定目录的文本文件中搜索关键词（递归）
     *
     * @param dirPath 目录路径
     * @param keyword 搜索关键词
     * @return Map：文件路径 -> 匹配行列表
     */
    @Tool(description = "在指定目录的文本文件中搜索关键词（递归），返回 文件路径->匹配行列表 的映射")
    public static Map<String, List<String>> searchTextInFiles(String dirPath, String keyword) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        List<File> textFiles = scanFilesByExtensions(dirPath, "txt", "java", "xml", "json",
                "yaml", "yml", "properties", "sql", "html", "css", "js", "ts", "md", "log");

        for (File file : textFiles) {
            try (Scanner scanner = new Scanner(file, "UTF-8")) {
                List<String> matchedLines = new ArrayList<>();
                int lineNum = 0;
                while (scanner.hasNextLine()) {
                    lineNum++;
                    String line = scanner.nextLine();
                    if (line.toLowerCase().contains(keyword.toLowerCase())) {
                        matchedLines.add("第" + lineNum + "行: " + line.trim());
                    }
                }
                if (!matchedLines.isEmpty()) {
                    result.put(file.getAbsolutePath(), matchedLines);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    // ==================== 7. 工具方法 ====================

    /**
     * 获取文件树结构（用于展示）
     *
     * @param dirPath 目录路径
     * @return 树形结构字符串
     */
    @Tool(description = "获取文件树结构（用于展示），返回格式化的树形字符串")
    public static String getFileTree(String dirPath) {
        StringBuilder sb = new StringBuilder();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        buildTree(dir, "", sb, true);
        return sb.toString();
    }

    /**
     * 递归构建文件树
     */
    private static void buildTree(File node, String prefix, StringBuilder sb, boolean isTail) {
        sb.append(prefix);
        sb.append(isTail ? "└── " : "├── ");
        sb.append(node.getName());
        if (node.isDirectory()) {
            sb.append("/");
        }
        sb.append("\n");

        if (node.isDirectory()) {
            File[] children = node.listFiles();
            if (children == null) return;
            // 目录排在前面
            List<File> sorted = Arrays.stream(children)
                    .sorted((a, b) -> {
                        if (a.isDirectory() && !b.isDirectory()) return -1;
                        if (!a.isDirectory() && b.isDirectory()) return 1;
                        return a.getName().compareToIgnoreCase(b.getName());
                    })
                    .collect(Collectors.toList());

            for (int i = 0; i < sorted.size(); i++) {
                buildTree(sorted.get(i), prefix + (isTail ? "    " : "│   "), sb, i == sorted.size() - 1);
            }
        }
    }

    /**
     * 获取文件统计信息
     *
     * @param dirPath 目录路径
     * @return 统计信息 Map
     */
    @Tool(description = "获取文件统计信息，包括总条目数、文件数、目录数、总大小、扩展名统计等")
    public static Map<String, Object> getFileStatistics(String dirPath) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<File> allFiles = scanFilesRecursively(dirPath);
        List<File> files = allFiles.stream().filter(File::isFile).collect(Collectors.toList());
        List<File> dirs = allFiles.stream().filter(File::isDirectory).collect(Collectors.toList());

        stats.put("总条目数", allFiles.size());
        stats.put("文件数", files.size());
        stats.put("目录数", dirs.size());

        // 总大小
        long totalSize = files.stream().mapToLong(File::length).sum();
        stats.put("总大小(字节)", totalSize);
        stats.put("总大小(可读)", formatFileSize(totalSize));

        // 扩展名统计
        Map<String, Long> extCount = files.stream()
                .map(f -> {
                    String name = f.getName();
                    int idx = name.lastIndexOf('.');
                    return idx == -1 ? "(无扩展名)" : name.substring(idx + 1).toLowerCase();
                })
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));
        stats.put("扩展名统计", extCount);

        return stats;
    }

    /**
     * 格式化文件大小
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ==================== main 测试方法 ====================

    public static void main(String[] args) {
        // 测试扫描
        String testPath = "."; // 当前目录

        System.out.println("===== 文件树 =====");
        System.out.println(getFileTree(testPath));

        System.out.println("\n===== 统计信息 =====");
        Map<String, Object> stats = getFileStatistics(testPath);
        stats.forEach((k, v) -> System.out.println(k + ": " + v));

        System.out.println("\n===== Java文件扫描 =====");
        List<File> javaFiles = scanFilesByExtensions(testPath, "java");
        javaFiles.forEach(f -> System.out.println(f.getAbsolutePath()));
    }
}
