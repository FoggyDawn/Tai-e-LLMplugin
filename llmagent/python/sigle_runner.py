import argparse

if __name__ == "__main__":
    # 接收Java传递的参数
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    # 执行核心逻辑
    result = DataAnalyzer().process(args.input)
