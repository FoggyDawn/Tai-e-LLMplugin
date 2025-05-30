from http import HTTPStatus
# 建议dashscope SDK 的版本 >= 1.14.0
from dashscope import Generation
from time import sleep
from memchat import MemChat

class LlamaMemChat(MemChat):

    def __init__(self, model= "llama3.1-8b-instruct"):
        # 初始化实例属性
        try:
            f = open("../oldstuff/keys/aliyun-apikey.txt", 'r')
            apikey = f.read()
        finally:
            if f:
                f.close()
        self.model = model
        MemChat.__init__(self,apikey=apikey)

    def chat_once(self):
        sleep(5)
        response = Generation.call(
            model= self.model,
            messages= self.memory,
            api_key = self.api_key,
            result_format='message'
        )
        if response.status_code == HTTPStatus.OK:
            self.memory.append({"role": "assistant", "content": response.output.choices[0].message.content})
            return response.output.choices[0].message.content
        else:
            print('Request id: %s, Status code: %s, error code: %s, error message: %s' % (
                response.request_id, response.status_code,
                response.code, response.message
            ))

    def save_memory(self,mission):
        f = open("../record/"+mission+"/llama.txt",'a')
        MemChat.save_memory(self,f)
        for context in self.memory:
            role = context["role"]
            content = context["content"]
            f.write(f"{role}: {content}\n\n")
        f.close()


if __name__ == '__main__':

    test = LlamaMemChat()
    print(test.prompt_entry("你好，很高兴认识你。"))
    print(test.prompt_entry("你能重复我上句话吗？"))
    print(test.prompt_entry("你能再重复我的上句话一次吗？"))
    test.show_memory()



# def call_with_messages():
#     messages = [{'role': 'system', 'content': 'You are a helpful assistant.'},
#                 {'role': 'user', 'content': '你是谁？'}]
#     response = Generation.call(model="qwen-turbo",
#                                messages=messages,
#                                # 将输出设置为"message"格式
#                                result_format='message')
    # if response.status_code == HTTPStatus.OK:
    #     print(response)
    # else:
    #     print('Request id: %s, Status code: %s, error code: %s, error message: %s' % (
    #         response.request_id, response.status_code,
    #         response.code, response.message
    #     ))
