from zhipuai import ZhipuAI

from memchat import MemChat

class ZhipuMemChat(MemChat):

    def __init__(self,model= "glm-4-flash"):
        # 初始化实例属性
        try:
            f = open("../keys/zhipu-apikey.txt", 'r')
            apikey =f.read()
        finally:
            if f:
                f.close()
        self.model = model
        MemChat.__init__(self,apikey=apikey)
        self.client = ZhipuAI(api_key=apikey) # 填写您自己的APIKey

    def chat_once(self):
        response = self.client.chat.completions.create(
            model=self.model,
            messages= self.memory
        )
        # print(response.choices[0].message.content)
        self.memory.append({"role": "assistant", "content": response.choices[0].message.content})
        return response.choices[0].message.content
    
    def save_memory(self,mission):
        f = open("../record/"+mission+"/zhipu.txt",'a')
        MemChat.save_memory(self,f)
        for context in self.memory:
            role = context["role"]
            content = context["content"]
            f.write(f"{role}: {content}\n\n")
        f.close()

    
if __name__ == "__main__":
        
    test = ZhipuMemChat()
    print(test.prompt_entry("你好，很高兴认识你。"))
    print(test.prompt_entry("你能重复我上句话吗？"))
    print(test.prompt_entry("你能再重复我的上句话一次吗？"))
    test.show_memory()

# response = client.chat.completions.create(
#     model="glm-4-flash",  # 填写需要调用的模型编码
#     messages=[
#         {"role": "user", "content": "作为一名营销专家，请为我的产品创作一个吸引人的slogan"},
#         {"role": "assistant", "content": "当然，为了创作一个吸引人的slogan，请告诉我一些关于您产品的信息"},
#         {"role": "user", "content": "智谱AI开放平台"},
#         {"role": "assistant", "content": "智启未来，谱绘无限一智谱AI，让创新触手可及!"},
#         {"role": "user", "content": "创造一个更精准、吸引人的slogan"}
#     ],
# )

# print(response.choices[0].message.content)

