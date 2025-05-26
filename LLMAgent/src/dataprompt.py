from llama import *
from deepseek import *
from qwen import *
from zhipu import *
from gpt import *

import re

def create_chat(model_name):
    # 定义正则表达式模式
    patterns = [
        (r'^glm', ZhipuMemChat),
        (r'^gpt', GPTMemChat),
        (r'^llama', LlamaMemChat),
        (r'^qwen', QwenMemChat),
        (r'^deepseek', DeepseekMemChat),
    ]

    # 匹配模型名称并创建对象
    for pattern, model_class in patterns:
        if re.match(pattern, model_name.lower()):
            return model_class(model_name)

    # 如果没有匹配，返回None或者抛出异常
    # return None
    # 或者你可以选择抛出一个异常
    raise ValueError(f"Unknown model name: {model_name}")


def read_dialogues(file_path):
    dialogues = []
    current_role = None
    current_content = []

    # 打开并读取文件
    with open(file_path, 'r+', encoding='utf-8') as file:
        for line in file:
            # 移除行尾的换行符
            # line = line.rstrip('\n')
            # 检查是否是新的角色
            if line.startswith("system:") or line.startswith("user:"):
                # 如果当前已经有对话，添加到对话列表
                if current_role:
                    # 将当前内容合并为一个字符串，并保留换行符
                    dialogues.append((current_role, '\n'.join(current_content)))
                # 开始新的对话
                current_role, first_line = line.split(":", 1)
                current_content = [first_line]  # 以第一行内容开始新的对话内容
            else:
                # 如果不是新角色，追加内容
                if line:  # 确保不是空行
                    current_content.append(line)

        # 添加最后一个对话
        if current_role:
            dialogues.append((current_role, '\n'.join(current_content)))

    return dialogues

def main(model, mission):

    chat = create_chat(model)

    requests = read_dialogues("../prompt/"+mission+".txt")
    for query in requests:
        role, content = query
        if role == "system":
            chat.set_memory([{"role": role, "content": content.replace('\n',' ')}])
        else :
            chat.prompt_entry(content.replace('\n',' '))

    chat.save_memory(mission)

main("gpt-4o-mini","DCE")
