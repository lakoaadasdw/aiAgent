package com.example.aiagent.Tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 扫描工具类
 * 提供文件系统扫描、目录扫描、包扫描、注解扫描等多种扫描方法
 */
@SuppressWarnings({"all"})
@Component
public class ScanTools implements AgentToolsInterface {

    // ==================== 1. 文件系统扫描 ====================

    /**
     * 扫描指定目录下的所有文件（递归）
     *
     * @param dirPath 目录路径
     * @return 文件列表字符串
     */
    @Tool(description = "扫描指定目录下的所有文件（递归），返回文件列表")
    public String scanFilesRecursively(@ToolParam(description = "目录路径") String dirPath) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);
        return result.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
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
     * @return 文件列表字符串
     */
    @Tool(description = "扫描指定目录下所有文件（非递归，仅当前层），返回文件列表")
    public String scanFilesFlat(@ToolParam(description = "目录路径") String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return "目录为空";
        }
        return Arrays.stream(files)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 扫描指定目录下所有文件，仅返回普通文件（非目录）
     *
     * @param dirPath 目录路径
     * @return 文件列表字符串
     */
    @Tool(description = "扫描指定目录下所有文件，仅返回普通文件（非目录），递归扫描")
    public String scanOnlyFiles(@ToolParam(description = "目录路径") String dirPath) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);
        String filesStr = result.stream()
                .filter(File::isFile)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
        return filesStr.isEmpty() ? "未找到任何文件" : filesStr;
    }

    /**
     * 扫描指定目录下所有子目录
     *
     * @param dirPath 目录路径
     * @return 目录列表字符串
     */
    @Tool(description = "扫描指定目录下所有子目录，递归扫描")
    public String scanDirectories(@ToolParam(description = "目录路径") String dirPath) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);
        String dirsStr = result.stream()
                .filter(File::isDirectory)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
        return dirsStr.isEmpty() ? "未找到任何子目录" : dirsStr;
    }

    // ==================== 2. 扩展名/文件名过滤扫描 ====================

    /**
     * 按扩展名过滤扫描文件（递归）
     *
     * @param dirPath    目录路径
     * @param extensions 扩展名列表，如 "java", "xml", "properties"
     * @return 匹配的文件列表字符串
     */
    @Tool(description = "按扩展名过滤扫描文件（递归），如 extensions='java','xml'")
    public String scanFilesByExtensions(@ToolParam(description = "目录路径") String dirPath,
                                        @ToolParam(description = "扩展名列表，如 'java','xml'") String extensions) {
        String[] extArray = extensions.split(",");
        Set<String> extSet = Arrays.stream(extArray)
                .map(String::trim)
                .map(e -> e.startsWith(".") ? e.toLowerCase() : "." + e.toLowerCase())
                .collect(Collectors.toSet());

        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);

        String matched = result.stream()
                .filter(File::isFile)
                .filter(f -> {
                    String name = f.getName().toLowerCase();
                    return extSet.stream().anyMatch(name::endsWith);
                })
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
        return matched.isEmpty() ? "未找到匹配的文件" : matched;
    }

    /**
     * 按文件名关键词过滤扫描文件（递归）
     *
     * @param dirPath 目录路径
     * @param keyword 文件名关键词
     * @return 匹配的文件列表字符串
     */
    @Tool(description = "按文件名关键词过滤扫描文件（递归），查找文件名中包含指定关键词的文件")
    public String scanFilesByNameKeyword(@ToolParam(description = "目录路径") String dirPath,
                                         @ToolParam(description = "文件名关键词") String keyword) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);

        String matched = result.stream()
                .filter(File::isFile)
                .filter(f -> f.getName().toLowerCase().contains(keyword.toLowerCase()))
                .map(File::getAbsolutePath)
                .collect(Collectors.joining("\n"));
        return matched.isEmpty() ? "未找到包含关键词「" + keyword + "」的文件" : matched;
    }

    /**
     * 按文件大小范围过滤扫描文件（递归）
     *
     * @param dirPath 目录路径
     * @param minSize 最小字节数（含），-1 表示不限制
     * @param maxSize 最大字节数（含），-1 表示不限制
     * @return 匹配的文件列表字符串
     */
    @Tool(description = "按文件大小范围过滤扫描文件（递归），minSize=-1表示不限制下限，maxSize=-1表示不限制上限")
    public String scanFilesBySize(@ToolParam(description = "目录路径") String dirPath,
                                  @ToolParam(description = "最小字节数（含），-1表示不限制") long minSize,
                                  @ToolParam(description = "最大字节数（含），-1表示不限制") long maxSize) {
        List<File> result = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, result);

        String matched = result.stream()
                .filter(File::isFile)
                .filter(f -> {
                    long len = f.length();
                    boolean passMin = minSize < 0 || len >= minSize;
                    boolean passMax = maxSize < 0 || len <= maxSize;
                    return passMin && passMax;
                })
                .map(f -> f.getAbsolutePath() + " (" + f.length() + " 字节)")
                .collect(Collectors.joining("\n"));
        return matched.isEmpty() ? "未找到匹配的文件" : matched;
    }

    // ==================== 3. 包扫描（Class扫描） ====================

    /**
     * 扫描指定包下的所有类名（基于类路径）
     *
     * @param packageName 包名，如 "com.example"
     * @return 类全限定名列表字符串
     */
    @Tool(description = "扫描指定包下的所有类名（基于类路径），返回类全限定名列表，如 packageName='com.example'")
    public String scanClassesInPackage(@ToolParam(description = "包名，如 com.example") String packageName) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }
        if (classNames.isEmpty()) {
            return "包「" + packageName + "」下未找到任何类";
        }
        return classNames.stream().collect(Collectors.joining("\n"));
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
     * @return 类全限定名列表字符串
     */
    @Tool(description = "扫描指定包下的所有类并加载为Class对象，返回类全限定名列表，如 packageName='com.example'")
    public String scanClasses(@ToolParam(description = "包名，如 com.example") String packageName) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }

        List<String> loaded = classNames.stream()
                .map(name -> {
                    try {
                        Class.forName(name);
                        return name + " (加载成功)";
                    } catch (ClassNotFoundException e) {
                        return name + " (加载失败)";
                    }
                })
                .collect(Collectors.toList());

        if (loaded.isEmpty()) {
            return "包「" + packageName + "」下未找到任何类";
        }
        return loaded.stream().collect(Collectors.joining("\n"));
    }

    // ==================== 4. 注解扫描 ====================

    /**
     * 扫描指定包下带有特定注解的类
     *
     * @param packageName  包名
     * @param annotation   注解全限定名，如 "org.springframework.stereotype.Service"
     * @return 带有该注解的类列表字符串
     */
    @Tool(description = "扫描指定包下带有特定注解的类，返回类全限定名列表，如 packageName='com.example', annotation='org.springframework.stereotype.Service'")
    public String scanAnnotatedClasses(@ToolParam(description = "包名，如 com.example") String packageName,
                                       @ToolParam(description = "注解全限定名，如 org.springframework.stereotype.Service") String annotation) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }

        Class<?> annotationClass;
        try {
            annotationClass = Class.forName(annotation);
        } catch (ClassNotFoundException e) {
            return "未找到注解类: " + annotation;
        }

        Class<?> finalAnnotationClass = annotationClass;
        List<String> result = classNames.stream()
                .map(name -> {
                    try {
                        Class<?> clazz = Class.forName(name);
                        if (clazz.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) finalAnnotationClass)) {
                            return name;
                        }
                        return null;
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            return "包「" + packageName + "」下未找到带有 @" + annotationClass.getSimpleName() + " 注解的类";
        }
        return result.stream().collect(Collectors.joining("\n"));
    }

    /**
     * 扫描指定类中带有特定注解的方法
     *
     * @param clazz      类全限定名
     * @param annotation 注解全限定名
     * @return 方法名列表字符串
     */
    @SuppressWarnings("unchecked")
    @Tool(description = "扫描指定类中带有特定注解的方法，返回方法名列表")
    public String scanAnnotatedMethods(@ToolParam(description = "类全限定名，如 com.example.MyClass") String clazz,
                                       @ToolParam(description = "注解全限定名，如 org.springframework.web.bind.annotation.GetMapping") String annotation) {
        Class<?> targetClass;
        Class<? extends java.lang.annotation.Annotation> annotationClass;
        try {
            targetClass = Class.forName(clazz);
            annotationClass = (Class<? extends java.lang.annotation.Annotation>) Class.forName(annotation);
        } catch (ClassNotFoundException e) {
            return "未找到类或注解: " + e.getMessage();
        }

        List<java.lang.reflect.Method> methods = Arrays.stream(targetClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(annotationClass))
                .collect(Collectors.toList());

        if (methods.isEmpty()) {
            return "类「" + clazz + "」中未找到带有 @" + annotationClass.getSimpleName() + " 注解的方法";
        }
        return methods.stream()
                .map(m -> m.getName() + "(" + Arrays.stream(m.getParameterTypes())
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(", ")) + ")")
                .collect(Collectors.joining("\n"));
    }

    /**
     * 扫描指定包下所有类中带有特定注解的方法
     *
     * @param packageName 包名
     * @param annotation  注解全限定名
     * @return 类名->方法列表 的字符串表示
     */
    @SuppressWarnings("unchecked")
    @Tool(description = "扫描指定包下所有类中带有特定注解的方法，返回 类名->方法列表 的映射")
    public String scanAnnotatedMethodsInPackage(@ToolParam(description = "包名，如 com.example") String packageName,
                                                @ToolParam(description = "注解全限定名，如 org.springframework.web.bind.annotation.GetMapping") String annotation) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }

        Class<? extends java.lang.annotation.Annotation> annotationClass;
        try {
            annotationClass = (Class<? extends java.lang.annotation.Annotation>) Class.forName(annotation);
        } catch (ClassNotFoundException e) {
            return "未找到注解类: " + annotation;
        }

        StringBuilder sb = new StringBuilder();
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                List<java.lang.reflect.Method> methods = Arrays.stream(clazz.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(annotationClass))
                        .collect(Collectors.toList());
                if (!methods.isEmpty()) {
                    sb.append("类: ").append(className).append("\n");
                    for (java.lang.reflect.Method m : methods) {
                        sb.append("  - ").append(m.getName()).append("(")
                                .append(Arrays.stream(m.getParameterTypes())
                                        .map(Class::getSimpleName)
                                        .collect(Collectors.joining(", ")))
                                .append(")\n");
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (sb.length() == 0) {
            return "包「" + packageName + "」下未找到带有 @" + annotationClass.getSimpleName() + " 注解的方法";
        }
        return sb.toString().trim();
    }

    // ==================== 5. 接口/父类扫描 ====================

    /**
     * 扫描指定包下实现了特定接口的所有类
     *
     * @param packageName   包名
     * @param interfaceClzz 接口全限定名
     * @return 实现该接口的类列表字符串
     */
    @Tool(description = "扫描指定包下实现了特定接口的所有类，返回类全限定名列表")
    public String scanClassesImplementing(@ToolParam(description = "包名，如 com.example") String packageName,
                                          @ToolParam(description = "接口全限定名，如 java.util.List") String interfaceClzz) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }

        Class<?> interfaceClass;
        try {
            interfaceClass = Class.forName(interfaceClzz);
        } catch (ClassNotFoundException e) {
            return "未找到接口类: " + interfaceClzz;
        }

        Class<?> finalInterfaceClass = interfaceClass;
        List<String> result = classNames.stream()
                .map(name -> {
                    try {
                        Class<?> clazz = Class.forName(name);
                        if (!clazz.equals(finalInterfaceClass) && finalInterfaceClass.isAssignableFrom(clazz)) {
                            return name;
                        }
                        return null;
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            return "包「" + packageName + "」下未找到实现 " + interfaceClass.getSimpleName() + " 接口的类";
        }
        return result.stream().collect(Collectors.joining("\n"));
    }

    /**
     * 扫描指定包下继承自特定父类的所有类
     *
     * @param packageName 包名
     * @param superClzz   父类全限定名
     * @return 子类列表字符串
     */
    @Tool(description = "扫描指定包下继承自特定父类的所有类，返回类全限定名列表")
    public String scanClassesExtending(@ToolParam(description = "包名，如 com.example") String packageName,
                                       @ToolParam(description = "父类全限定名，如 java.util.ArrayList") String superClzz) {
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
            return "扫描包失败: " + packageName + " - " + e.getMessage();
        }

        Class<?> superClass;
        try {
            superClass = Class.forName(superClzz);
        } catch (ClassNotFoundException e) {
            return "未找到父类: " + superClzz;
        }

        Class<?> finalSuperClass = superClass;
        List<String> result = classNames.stream()
                .map(name -> {
                    try {
                        Class<?> clazz = Class.forName(name);
                        if (!clazz.equals(finalSuperClass) && finalSuperClass.isAssignableFrom(clazz)) {
                            return name;
                        }
                        return null;
                    } catch (ClassNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            return "包「" + packageName + "」下未找到继承自 " + superClass.getSimpleName() + " 的类";
        }
        return result.stream().collect(Collectors.joining("\n"));
    }

    // ==================== 6. 内容扫描（文本搜索） ====================

    /**
     * 在指定目录的文本文件中搜索关键词（递归）
     *
     * @param dirPath 目录路径
     * @param keyword 搜索关键词
     * @return 搜索结果字符串
     */
    @Tool(description = "在指定目录的文本文件中搜索关键词（递归），返回 文件路径->匹配行列表 的映射")
    public String searchTextInFiles(@ToolParam(description = "目录路径") String dirPath,
                                    @ToolParam(description = "搜索关键词") String keyword) {
        List<File> allFiles = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, allFiles);

        List<File> textFiles = allFiles.stream()
                .filter(File::isFile)
                .filter(f -> {
                    String name = f.getName().toLowerCase();
                    return name.endsWith(".txt") || name.endsWith(".java") || name.endsWith(".xml")
                            || name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")
                            || name.endsWith(".properties") || name.endsWith(".sql") || name.endsWith(".html")
                            || name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".ts")
                            || name.endsWith(".md") || name.endsWith(".log") || name.endsWith(".csv")
                            || name.endsWith(".py") || name.endsWith(".yml");
                })
                .collect(Collectors.toList());

        if (textFiles.isEmpty()) {
            return "目录「" + dirPath + "」下未找到可搜索的文本文件";
        }

        StringBuilder sb = new StringBuilder();
        int totalMatchedFiles = 0;
        int totalMatchedLines = 0;

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
                    totalMatchedFiles++;
                    totalMatchedLines += matchedLines.size();
                    sb.append("=== ").append(file.getAbsolutePath()).append(" ===\n");
                    for (String matchedLine : matchedLines) {
                        sb.append(matchedLine).append("\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception ignored) {
            }
        }

        if (totalMatchedFiles == 0) {
            return "未在目录「" + dirPath + "」中找到包含「" + keyword + "」的内容";
        }

        return "搜索完成！共在 " + totalMatchedFiles + " 个文件中找到 " + totalMatchedLines + " 处匹配：\n\n"
                + sb.toString().trim();
    }

    // ==================== 7. 工具方法 ====================

    /**
     * 获取文件树结构（用于展示）
     *
     * @param dirPath 目录路径
     * @return 树形结构字符串
     */
    @Tool(description = "获取文件树结构（用于展示），返回格式化的树形字符串")
    public String getFileTree(@ToolParam(description = "目录路径") String dirPath) {
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
     * @return 统计信息字符串
     */
    @Tool(description = "获取文件统计信息，包括总条目数、文件数、目录数、总大小、扩展名统计等")
    public String getFileStatistics(@ToolParam(description = "目录路径") String dirPath) {
        List<File> allFiles = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return "目录不存在: " + dirPath;
        }
        scanFilesRecursive(dir, allFiles);

        List<File> files = allFiles.stream().filter(File::isFile).collect(Collectors.toList());
        List<File> dirs = allFiles.stream().filter(File::isDirectory).collect(Collectors.toList());

        long totalSize = files.stream().mapToLong(File::length).sum();

        // 扩展名统计
        Map<String, Long> extCount = files.stream()
                .map(f -> {
                    String name = f.getName();
                    int idx = name.lastIndexOf('.');
                    return idx == -1 ? "(无扩展名)" : name.substring(idx + 1).toLowerCase();
                })
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("===== 文件统计信息 =====\n");
        sb.append("目录路径: ").append(dirPath).append("\n");
        sb.append("总条目数: ").append(allFiles.size()).append("\n");
        sb.append("文件数: ").append(files.size()).append("\n");
        sb.append("目录数: ").append(dirs.size()).append("\n");
        sb.append("总大小: ").append(formatFileSize(totalSize)).append(" (").append(totalSize).append(" 字节)\n");
        sb.append("\n--- 扩展名统计 ---\n");
        extCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(entry -> sb.append(".").append(entry.getKey()).append(": ").append(entry.getValue()).append(" 个文件\n"));

        return sb.toString();
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
}
