from openai import OpenAI

from memchat import MemChat

class DeepseekMemChat(MemChat):

    def __init__(self,model = "deepseek-chat"):
        # 初始化实例属性
        try:
            f = open("../oldstuff/keys/deepseek-apikey.txt", 'r')
            apikey = f.read()
        finally:
            if f:
                f.close()
        self.model = model
        MemChat.__init__(self,apikey=apikey)
        self.client = OpenAI(api_key=apikey, base_url="https://api.deepseek.com") # 填写您自己的APIKey

    def chat_once(self):
        response = self.client.chat.completions.create(
            model=self.model,
            messages= self.memory
        )
        # print(response.choices[0].message.content)
        self.memory.append({"role": "assistant", "content": response.choices[0].message.content})
        return response.choices[0].message.content

    def save_memory(self,mission):
        f = open("../record/"+mission+"/deepseek.txt",'a')
        MemChat.save_memory(self,f)
        for context in self.memory:
            role = context["role"]
            content = context["content"]
            f.write(f"{role}: {content}\n\n")
        f.close()


if __name__ == "__main__":

    test = DeepseekMemChat()
    print(test.prompt_entry("你好，很高兴认识你。"))
    print(test.prompt_entry("你能重复我上句话吗？"))
    print(test.prompt_entry("你能再重复我的上句话一次吗？"))
    test.show_memory()


# print(response.choices[0].message.content)
