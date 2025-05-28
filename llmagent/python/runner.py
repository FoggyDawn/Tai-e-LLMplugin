import argparse
import json

import sys
import threading


def handle_java_command(command):
    pass


def input_listener():
    while True:
        command = sys.stdin.readline().strip()
        if command.startswith("RESPONSE_DATA"):
            handle_java_command(command)

def main_logic():
    while True:
        # 需要Java数据时主动请求
        print("REQUEST_DATA:user_12345", flush=True)
        # 继续执行其他操作...

if __name__ == "__main__":
    threading.Thread(target=input_listener).start()
    main_logic()
