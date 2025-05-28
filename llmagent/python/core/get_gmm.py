import numpy as np
from sklearn.decomposition import PCA
from sklearn.mixture import GaussianMixture
from sklearn.datasets import make_blobs
from sklearn.preprocessing import StandardScaler
import matplotlib.pyplot as plt

# 1. 生成模拟数据（7维示例）
X, _ = make_blobs(n_samples=300, n_features=7, centers=4, cluster_std=0.6, random_state=0)
X = StandardScaler().fit_transform(X)  # 标准化

# 2. 创建GMM模型（核心修改点）
gmm = GaussianMixture(
    n_components=4,            # 初始假设簇数（后续可优化）
    covariance_type='full',    # 协方差矩阵类型（支持球形/非球形簇）
    max_iter=300,              # 最大迭代次数
    random_state=0
)

# 3. 训练与预测
gmm.fit(X)
labels = gmm.predict(X)        # 硬聚类标签
probs = gmm.predict_proba(X)   # 每个样本属于各簇的概率（软聚类）

# 4. 可视化（PCA降维）
pca = PCA(n_components=2)
X_2d = pca.fit_transform(X)

plt.scatter(X_2d[:, 0], X_2d[:, 1], c=labels, cmap='viridis', s=50, alpha=0.7)
plt.title("GMM Clustering (7D→2D)")
plt.show()
