import argparse
import json




class DataAnalyzer:

    def __init__(self):
        self.data = None

    def read_java_map(this, file_path):
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        for key, value in data.items():
            assert isinstance(value, list), "值必须是列表"
            assert all(isinstance(num, (int, float)) for num in value), "列表元素必须为数字"

        return data

    def process(self, input_path):
        # print("input file: ", input_path)
        self.data = self.read_java_map(input_path)
        # TODO: fill in gmm process
        return list(self.data.keys())[:50]


if __name__ == "__main__":
    # 接收Java传递的参数（网页6）
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    # 执行核心逻辑
    result = DataAnalyzer().process(args.input)
    # print(json.dumps(result))
    with open("llmagent/io/valuablemethod.json", "w", encoding="utf-8") as f:
        json.dump(result, f, indent=4, ensure_ascii=False)
