import asyncio
from autogen_agentchat.agents import AssistantAgent
from autogen_agentchat.messages import TextMessage
from autogen_agentchat.ui import Console
from autogen_ext.models.openai import OpenAIChatCompletionClient
from autogen_core import CancellationToken

async def main():
    model_client = OpenAIChatCompletionClient(model="gpt-4o")
    assistant = AssistantAgent(
        name="assistant",
        model_client=model_client,
        system_message="You are a helpful AI assistant.",
    )

    response = await assistant.on_messages(
        messages=[TextMessage(content="你好，请写一个关于秋天的三行诗", source="user")],
        cancellation_token=CancellationToken()
    )
    print(response.chat_message.content)

asyncio.run(main())
