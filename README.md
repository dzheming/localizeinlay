# Localize Argument Inlay Plugin

## 项目简介

Localize Inlay 是一个 IntelliJ IDEA 插件，用于在 Java / C# 方法调用中，当整数字面量实参命中 JSON 配置中的 `sn` 时，在参数后内联显示对应的 `str` 文本。

## 功能特点

- **内联提示**：在整数字面量参数后显示对应的本地化文本
- **支持多种调用形式**：
  - 普通调用：`LocalUtils.GetString(1001)`
  - 嵌套调用：`LocalUtils.GetString(LocalUtils.GetString(1001))`
  - 条件表达式：`LocalUtils.GetString(true ? 1001 : 1002)`
  - 多参数调用：`LocalUtils.GetString(1001, "param", 1002)`
- **空格处理**：支持方法名和括号之间的任意空格
- **JSON 配置**：通过 JSON 文件配置 `sn` 到 `str` 的映射
- **可自定义配置路径**：在插件设置中自定义 JSON 配置文件路径

## 安装方法

### 从本地安装
1. 下载插件的 ZIP 文件
2. 打开 IntelliJ IDEA
3. 进入 `File > Settings > Plugins`
4. 点击 "Install Plugin from Disk..."
5. 选择下载的 ZIP 文件
6. 重启 IntelliJ IDEA

## 使用方法

1. **配置 JSON 文件**：创建一个 JSON 文件，包含 `sn` 和 `str` 字段的映射，例如：
   ```json
   [
     { "sn": 1001, "str": "欢迎使用系统" },
     { "sn": 1002, "str": "登录成功" },
     { "sn": 1003, "str": "退出系统" }
   ]
   ```

2. **配置插件**：
   - 进入 `File > Settings > Tools > Localize Argument Inlay`
   - 在 "JSON 配置路径" 中输入 JSON 文件的路径
   - 点击 "Apply" 保存配置

3. **使用插件**：在代码中使用 `LocalUtils.GetString()` 方法时，插件会自动在整数字面量参数后显示对应的本地化文本。

## 配置说明

### JSON 配置格式

JSON 文件应该包含一个对象数组，每个对象包含 `sn` 和 `str` 字段：

```json
[
  { "sn": 1001, "str": "本地化文本1" },
  { "sn": 1002, "str": "本地化文本2" }
]
```

### 插件设置

- **JSON 配置路径**：JSON 配置文件的绝对路径
- **默认路径**：`ConfLocalize.json`

## 示例

### 输入

```java
// 普通调用
String text = LocalUtils.GetString(1001);

// 嵌套调用
String nestedText = LocalUtils.GetString(LocalUtils.GetString(1002));

// 条件表达式
String statusText = LocalUtils.GetString(isActive ? 1001 : 1003);

// 多参数调用
String multiParamText = LocalUtils.GetString(1001, "extra", LocalUtils.GetString(1002));
```

### 输出

```java
// 普通调用（显示内联提示）
String text = LocalUtils.GetString(1001 /* 欢迎使用系统 */);

// 嵌套调用（显示内联提示）
String nestedText = LocalUtils.GetString(LocalUtils.GetString(1002 /* 登录成功 */));

// 条件表达式（显示内联提示）
String statusText = LocalUtils.GetString(isActive ? 1001 /* 欢迎使用系统 */ : 1003 /* 退出系统 */);

// 多参数调用（显示内联提示）
String multiParamText = LocalUtils.GetString(1001 /* 欢迎使用系统 */, "extra", LocalUtils.GetString(1002 /* 登录成功 */));
```

## 开发指南

### 环境要求

- JDK 17+
- IntelliJ IDEA
- Gradle

### 构建项目

1. 克隆项目：
   ```bash
   git clone <项目地址>
   cd localize-argument-inlay-plugin
   ```

2. 构建项目：
   ```bash
   ./gradlew build
   ```

3. 运行插件：
   ```bash
   ./gradlew runIde
   ```

### 项目结构

```
localizeinlay/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/zmabel/localizeinlay/
│   │   │       ├── LocalizeInlayConfigurable.kt         # 插件配置界面
│   │   │       ├── LocalizeInlayHintsProvider.kt        # 核心提示提供者
│   │   │       ├── LocalizeInlayHintsProviderFactory.kt # 提示提供者工厂
│   │   │       ├── LocalizeInlayHintsProviderPlus.kt    # 增强版提示提供者
│   │   │       ├── LocalizeInlaySettingsState.kt        # 设置状态管理
│   │   │       └── SnJsonConfigMatcher.kt               # JSON 配置匹配器
│   │   ├── resources/
│   │   │   ├── META-INF/
│   │   │   │   └── plugin.xml                           # 插件配置
│   │   │   └── messages/
│   │   │       └── LocalizeInlayBundle.properties       # 国际化资源
├── build.gradle.kts                                      # 构建脚本
└── README.md                                             # 项目说明
```

## 联系方式

- 作者：zmabel
- 邮箱：<dzheming@163.com>
- 项目地址：<https://github.com/dzheming/localizeinlay.git>
