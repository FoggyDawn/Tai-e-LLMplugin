
from time import *

class MemChat:
    
    def __init__(self,apikey):
        # 初始化实例属性
        self.api_key = apikey # 填写您自己的APIKey
        self.memory = []

    def chat_once(self):
        return "no specific model selected"

    def prompt_entry(self, prompt):
        self.memory.append({"role": "user", "content": prompt})
        return  self.chat_once()
    
    def show_memory(self):
        for context in self.memory:
            role = context["role"]
            content = context["content"]
            print(f"{role}: {content}")

    def set_memory(self, mem):
        self.memory = mem

    def save_memory(self, f):
        f.write(f"\n\n============================= {strftime("%Y-%m-%d %H:%M:%S", localtime(time()))} - model: {self.model} =============================\n\n")