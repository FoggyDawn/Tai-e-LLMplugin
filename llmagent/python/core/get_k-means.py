import numpy as np
import matplotlib.pyplot as plt
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score

# 1. 生成示例数据（替换为你的真实数据）
np.random.seed(42)
data = np.random.randn(300, 7)  # 300个样本，7个特征
data = StandardScaler().fit_transform(data)  # 标准化

# 2. 自动寻找最佳K值（设置测试范围）
k_range = range(2, 11)
elbow_scores = []
silhouette_scores = []

for k in k_range:
    model = KMeans(n_clusters=k, n_init=10)
    model.fit(data)

    # 肘部法则指标：inertia（样本到最近聚类中心的平方距离之和）
    elbow_scores.append(model.inertia_)

    # 轮廓系数指标
    silhouette_scores.append(silhouette_score(data, model.labels_))

# 3. 可视化评估指标
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4))

# 肘部法则图
ax1.plot(k_range, elbow_scores, 'bo-')
ax1.set_title('Elbow Method')
ax1.set_xlabel('Number of clusters')
ax1.set_ylabel('Inertia')

# 轮廓系数图
ax2.plot(k_range, silhouette_scores, 'ro-')
ax2.set_title('Silhouette Score')
ax2.set_xlabel('Number of clusters')
ax2.set_ylabel('Score')

plt.tight_layout()
plt.show()

# 4. 自动选择最佳K值（这里使用轮廓系数最大化策略）
best_k = k_range[np.argmax(silhouette_scores) + 1]  # +1因为k从2开始
print(f"Recommended clusters: {best_k}")

# 5. 使用最佳K进行最终聚类
final_model = KMeans(n_clusters=best_k, n_init=10).fit(data)

# 6. 降维可视化（使用PCA将7维降至2维）
pca = PCA(n_components=2)
data_2d = pca.fit_transform(data)

plt.figure(figsize=(8, 6))
plt.scatter(data_2d[:, 0], data_2d[:, 1],
            c=final_model.labels_,
            cmap='tab10',
            s=50,
            alpha=0.6)

# 标记聚类中心（在PCA空间）
centers_2d = pca.transform(final_model.cluster_centers_)
plt.scatter(centers_2d[:, 0], centers_2d[:, 1],
            c='red',
            s=200,
            marker='X',
            edgecolor='black')

plt.title(f'7D Data Clustering (PCA Projection)\nK={best_k}')
plt.xlabel('Principal Component 1')
plt.ylabel('Principal Component 2')
plt.show()
