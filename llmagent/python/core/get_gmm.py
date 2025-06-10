import numpy as np
from sklearn.mixture import GaussianMixture
from sklearn.preprocessing import StandardScaler

class GMMCluster:
    def __init__(self, min_components=2, max_components=10, covariance_type='full', random_state=42):
        """
        初始化 GMM 聚类器

        参数:
        max_components: 最大聚类数量（默认10）
        covariance_type: 协方差类型 ('full', 'tied', 'diag', 'spherical')
        random_state: 随机种子
        """
        self.max_components = max_components
        self.min_components = min_components
        self.covariance_type = covariance_type
        self.random_state = random_state
        self.scaler = StandardScaler()
        self.best_gmm = None
        self.best_k = None

    def fit(self, data_dict):
        """
        对输入数据进行聚类分析

        参数:
        data_dict: 字典，键为字符串，值为长度为7的数字列表

        返回:
        labels_dict: 每个键对应的聚类标签
        cluster_info: 每个聚类的详细信息
        """
        # 1. 数据准备
        keys = list(data_dict.keys())
        data = np.array([data_dict[key] for key in keys])

        # 2. 数据标准化
        data_scaled = self.scaler.fit_transform(data)

        # 3. 自动选择最优聚类数
        self._select_best_k(data_scaled)

        # 4. 使用最优模型进行聚类
        labels = self.best_gmm.predict(data_scaled)

        # 5. 构建结果字典
        labels_dict = {key: int(label) for key, label in zip(keys, labels)}

        # 6. 构建聚类信息
        cluster_info = self._get_cluster_info()

        return labels_dict, cluster_info

    def _select_best_k(self, data):
        """使用BIC准则选择最优聚类数"""
        bic_scores = []
        aic_scores = []

        # 尝试不同聚类数
        for k in range(self.min_components, self.max_components + 1):
            gmm = GaussianMixture(
                n_components=k,
                covariance_type=self.covariance_type,
                random_state=self.random_state,
                n_init=10  # 多次初始化以避免局部最优
            )
            gmm.fit(data)
            bic_scores.append(gmm.bic(data))
            aic_scores.append(gmm.aic(data))

        # 选择BIC最小的模型（考虑模型复杂度）
        self.best_k = np.argmin(bic_scores) + 1  # +1 因为索引从0开始
        self.best_gmm = GaussianMixture(
            n_components=self.best_k,
            covariance_type=self.covariance_type,
            random_state=self.random_state,
            n_init=10
        ).fit(data)

    def _get_cluster_info(self):
        """获取每个聚类的详细信息"""
        cluster_info = []

        # 将中心点逆标准化到原始尺度
        centers_scaled = self.best_gmm.means_
        centers_original = self.scaler.inverse_transform(centers_scaled)

        for i in range(self.best_k):
            cluster_data = {
                'cluster_id': i,
                'center': centers_original[i].tolist(),  # 聚类中心
                'weight': float(self.best_gmm.weights_[i]),  # 混合权重
                'covariance': self.best_gmm.covariances_[i].tolist()  # 协方差矩阵
            }
            cluster_info.append(cluster_data)

        return cluster_info
