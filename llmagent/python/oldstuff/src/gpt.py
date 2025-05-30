from openai import OpenAI

from memchat import MemChat

class GPTMemChat(MemChat):

    def __init__(self,model= "gpt-4o-mini"):
        # 初始化实例属性
        try:
            f = open("../oldstuff/keys/openai-apikey.txt", 'r')
            apikey =f.read()
            apikey = apikey.rstrip("\n")
        finally:
            if f:
                f.close()
        self.model = model
        MemChat.__init__(self,apikey=apikey)
        self.client = OpenAI(api_key=apikey,base_url="https://api.chatanywhere.tech/v1") # 填写您自己的APIKey

    def chat_once(self):
        response = self.client.chat.completions.create(
            model=self.model,
            messages= self.memory
        )
        # print(response.choices[0].message.content)
        self.memory.append({"role": "assistant", "content": response.choices[0].message.content})
        return response.choices[0].message.content

    def save_memory(self,mission):
        f = open("../record/"+mission+"/gpt.txt",'a')
        MemChat.save_memory(self,f)
        for context in self.memory:
            role = context["role"]
            content = context["content"]
            f.write(f"{role}: {content}\n\n")
        f.close()

if __name__ == "__main__":

    test = GPTMemChat()
    print(test.prompt_entry("你好，很高兴认识你。"))
    print(test.prompt_entry("你能重复我上句话吗？"))
    print(test.prompt_entry("你能再重复我的上句话一次吗？"))
    test.show_memory()




# client = OpenAI(api_key=)

# completion = client.chat.completions.create(
#     model="gpt-4o-mini",
#     messages=[
#         {"role": "system", "content": "You are a helpful assistant."},
#         {
#             "role": "user",
#             "content": "Write a haiku about recursion in programming."
#         }
#     ]
# )

# print(completion.choices[0].message)
