import os
import re
import subprocess
import shutil
import sys


def extract_method_name(signature):
    """
    从函数签名中提取方法名
    格式：returnType methodName(paramType1,paramType2)
    """
    return signature.split('(')[0].split()[-1]

class CFRDecompiler:
    def __init__(self):
        self.cfr_path = self._get_cfr_path()

    def _get_cfr_path(self):
        """自动获取/下载CFR工具"""
        cfr_dir = os.path.join(os.path.expanduser("~"), ".cfr_decompiler")
        os.makedirs(cfr_dir, exist_ok=True)
        cfr_jar = os.path.join(cfr_dir, "cfr.jar")

        if not os.path.exists(cfr_jar):
            version = "0.152"
            url = f"https://repo1.maven.org/maven2/org/benf/cfr/{version}/cfr-{version}.jar"
            if sys.platform == "win32":
                subprocess.run(f"curl -L -o {cfr_jar} {url}", shell=True)
            else:
                subprocess.run(f"wget {url} -O {cfr_jar}", shell=True)
        return cfr_jar

    def decompile_method(self, classpath, method_ref):
        """
        反编译指定方法（直接匹配完整签名）
        :param classpath: 类路径(多个路径用:或;分隔)
        :param method_ref: 完整方法签名
           格式：com.example.Class: returnType methodName(paramType1,paramType2)
        """
        # 解析方法引用
        class_name, method_signature = method_ref.split(":", 1)
        method_signature = method_signature.strip()
        method_name = extract_method_name(method_signature)

        # 创建临时工作目录
        work_dir = os.path.abspath("/home/byx/projects/Tai-e-LLMplugin/llmagent/io/script_workdir")
        java_file = os.path.join(work_dir, f"{class_name.replace(".", "/")}.java")

        try:
            # 构建CFR命令
            cmd = [
                "java", "-jar", self.cfr_path,
                class_name.replace(".", "/"),
                "--outputdir", work_dir,
                "--methodname", method_name
            ]

            # 添加类路径
            if classpath:
                path_sep = ";" if sys.platform == "win32" else ":"
                cmd.extend(["--extraclasspath", path_sep.join(classpath.split(":"))])

            # 执行反编译
            result = subprocess.run(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )

            if result.returncode != 0:
                raise Exception(f"CFR error: {result.stderr}")

            # 读取反编译结果
            if not os.path.exists(java_file):
                raise FileNotFoundError(f"反编译失败: {class_name}")

            with open(java_file, "r", encoding="utf-8") as f:
                decompiled_code = f.read()

            return decompiled_code

        finally:
            shutil.rmtree(work_dir, ignore_errors=True)

# 使用示例
if __name__ == "__main__":
    decompiler = CFRDecompiler()

    # 直接使用完整签名反编译
    print(decompiler.decompile_method(
        classpath="/home/byx/testSpace/javaprjs/jasml-0.10:/home/byx/testSpace/javaprjs/fatDep.jar",
        method_ref="org.apache.crimson.tree.AttributeNode: void checkArguments(java.lang.String,java.lang.String)"
    ))

    # print(decompiler.decompile_method(
    #     classpath="/home/byx/testSpace/javaprjs/jedit/3.0/jedit.jar",
    #     method_ref="javax.swing.JComponent: void paintToOffscreen(java.awt.Graphics,int,int,int,int,int,int)"
    # ))
