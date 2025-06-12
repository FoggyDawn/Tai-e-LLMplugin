import asyncio
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.messages import TextMessage
from autogen_agentchat.ui import Console
from autogen_ext.models.openai import OpenAIChatCompletionClient
from autogen_core import CancellationToken

class Chat:

    def __init__(self):
        self.gpt_4o_client = OpenAIChatCompletionClient(
            model="gpt-4o",
            api_key="sk-UK10J76qxsDOfLgbl8DmUsBW2XSvkFGT6Gbgh00KKzCMXBYJ",
            base_url="https://api.chatanywhere.tech/v1"
        )
        self.gpt_4o_mini_client = OpenAIChatCompletionClient(
            model="gpt-4o-mini",
            api_key="sk-UK10J76qxsDOfLgbl8DmUsBW2XSvkFGT6Gbgh00KKzCMXBYJ",
            base_url="https://api.chatanywhere.tech/v1"
        )
        # 配置模型信息
        deepseek_model_info = {
            "family": "gpt-4o",  # 必填字段，model属于的类别
            "functions": [],  # 非必填字段，如果模型支持函数调用，可以在这里定义函数信息
            "vision": False,  # 必填字段，模型是否支持图像输入
            "json_output": True,  # 必填字段，模型是否支持json格式输出
            "function_calling": True,  # 必填字段，模型是否支持函数调用，如果模型需要使用工具函数，该字段为true
            "structured_output": True
        }
        qwen_model_info = {
            "family": "gpt-4o",  # 必填字段，model属于的类别
            "functions": [],  # 非必填字段，如果模型支持函数调用，可以在这里定义函数信息
            "vision": False,  # 必填字段，模型是否支持图像输入
            "json_output": True,  # 必填字段，模型是否支持json格式输出
            "function_calling": True,  # 必填字段，模型是否支持函数调用，如果模型需要使用工具函数，该字段为true
            "structured_output": True
        }
        self.deepseek_client = OpenAIChatCompletionClient(
            model="deepseek-chat",
            api_key="sk-db54f715f7c8488eb1f0fb29f7e0574b",
            base_url="https://api.deepseek.com",
            model_info=deepseek_model_info
        )
        self.qwen3_client = OpenAIChatCompletionClient(
            model="qwen3-235b-a22b",
            api_key="sk-63dd5fa8deab4407a0964316394aa3c2",
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
            model_info=qwen_model_info
        )

async def main():
    chat = Chat()

    assistant = AssistantAgent(
        name="assistant",
        model_client=chat.qwen3_client,
        system_message="You are a helpful AI assistant.",
    )

    response = await assistant.on_messages(
        messages=[TextMessage(content="你好，请写一个关于秋天的三行诗", source="user")],
        cancellation_token=CancellationToken()
    )
    print(response.chat_message.content)

asyncio.run(main())
