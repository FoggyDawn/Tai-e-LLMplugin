import argparse
import json
import sys

from core import GMMCluster




class DataAnalyzer:

    def __init__(self):
        self.data = None

    def read_java_map(self, file_path):
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        for key, value in data.items():
            assert isinstance(value, list), "值必须是列表"
            assert all(isinstance(num, (int, float)) for num in value), "列表元素必须为数字"

        return data

    def print_clusters(self, cluster_info):
        original_stdout = sys.stdout  # 保存原始输出流

        with open('llmagent/io/clusters.txt', 'w') as f:
            sys.stdout = f  # 重定向到文件
            # print("聚类标签字典:")
            # for key, label in labels_dict.items():
            #     print(f"{key}: Cluster {label}")

            print("\n聚类详细信息:")
            for cluster in cluster_info:
                print(f"Cluster {cluster['cluster_id']}:")
                print("callSiteNumber, primitiveArgNumber, otherArgNumber, subMethodIsAppNumber, subMethodLineNumber, subMethodCallSiteNumber, subMethodBranchNumber")

                # 格式化中心点（保留4位小数，极小小数归零）
                formatted_center = []
                for val in cluster['center']:
                    if abs(val) < 1e-5 and 'e' in f"{val}":
                        formatted_center.append(0.0)
                    else:
                        formatted_center.append(round(val, 4))
                print(f"  Center: {formatted_center}")

                # 格式化权重（百分比形式，保留1位小数）
                weight_percent = cluster['weight'] * 100
                print(f"  Weight: {weight_percent:.3f}%")

            sys.stdout = original_stdout  # 恢复原始输出流（重要！）

    def process(self, input_path):
        # print("input file: ", input_path)
        self.data = self.read_java_map(input_path)
        # gmm process
        cluster_getter = GMMCluster(min_components = 2, max_components=10)
        labels_dict, cluster_info = cluster_getter.fit(self.data)

        # 输出结果
        self.print_clusters(cluster_info)

        # TODO: select clusters and reach limit
        for k,v in labels_dict:



        return list(self.data.keys())[:500]


if __name__ == "__main__":
    # 接收Java传递的参数（网页6）
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    # 执行核心逻辑
    result = DataAnalyzer().process(args.input)

    print(json.dumps(result))
    # file method, slow but stable
    # with open("llmagent/io/valuablemethod.json", "w", encoding="utf-8") as f:
    #     json.dump(result, f, indent=4, ensure_ascii=False)
