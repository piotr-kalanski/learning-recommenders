library(dplyr)
library(ggplot2)

setwd("/home/piotrek/projects/learning-recommenders/eval-assignment")

results = read.csv('build/eval-results.csv')
head(results)

static_results = results %>% filter(is.na(NNbrs))
head(static_results)

results_aggregated = results %>%
  group_by(Algorithm, NNbrs) %>%
  summarise(
    RMSE.ByRating = mean(RMSE.ByRating),
    RMSE.ByUser = mean(RMSE.ByUser),
    Predict.nDCG = mean(Predict.nDCG),
    TopN.nDCG = mean(TopN.nDCG),
    MRR = mean(MRR),
    MAP = mean(MAP),
    TopN.TagEntropy = mean(TopN.TagEntropy)
  )

# 2. Which algorithm has the best RMSE?

ggplot(results_aggregated) +
  aes(x=Algorithm, y=RMSE.ByRating) +
  geom_boxplot()

ggplot(results_aggregated) +
  aes(x=Algorithm, y=RMSE.ByUser) +
  geom_boxplot()

results_aggregated %>%
  group_by(Algorithm) %>%
  summarise(
    min_rmse = min(RMSE.ByUser),
    mean_rmse = mean(RMSE.ByUser),
    min_rmse_rating = min(RMSE.ByRating),
    mean_rmse_rating = mean(RMSE.ByRating)
  ) %>%
  arrange(mean_rmse)

  # answer: Lucene norm?

# 3. Which algorithm has the best MAP?

ggplot(results_aggregated) +
  aes(x=Algorithm, y=MAP) +
  geom_boxplot()

results_aggregated %>%
  group_by(Algorithm) %>%
  summarise(
    min_map = min(MAP),
    mean_map = mean(MAP)
  ) %>%
  arrange(min_map)

# 4. Which of the following algorithms beat the personalized mean on RMSE (select all)?

ggplot(results_aggregated) +
  aes(x=Algorithm, y=RMSE.ByUser) +
  geom_boxplot()

ggplot(results_aggregated) +
  aes(x=Algorithm, y=RMSE.ByRating) +
  geom_boxplot()

# 5. What is the best neighborhood size for user-user to produce accurate predictions and rankings?

ggplot(results %>% filter(Algorithm == "UserUser")) +
  aes(x=factor(NNbrs), y=Coverage) +
  geom_boxplot()

ggplot(results %>% filter(Algorithm == "UserUser")) +
  aes(x=factor(NNbrs), y=TopN.nDCG) +
  geom_boxplot()

ggplot(results %>% filter(Algorithm == "UserUser")) +
  aes(x=factor(NNbrs), y=Predict.nDCG) +
  geom_boxplot()

results_aggregated %>%
  filter(Algorithm == "UserUser") %>%
  group_by(NNbrs) %>%
  summarise(
    mean_Predict.nDCG = mean(Predict.nDCG),
    mean_TopN.nDCG = mean(TopN.nDCG)
  ) %>%
  arrange(desc(mean_TopN.nDCG))

 # answer: 100 ?

# 6. What is the best personalized algorithm family for top-N recommendations on this data?

ggplot(results) +
  aes(x=Algorithm, y=TopN.nDCG) +
  geom_boxplot()

# 7. Do the top-N metrics generally agree or disagree?

# 8. As the neighborhood size increases, what happens to user-user’s top-N accuracy on this data?

ggplot(results %>% filter(Algorithm == "UserUser")) +
  aes(x=factor(NNbrs), y=TopN.nDCG) +
  geom_boxplot()

# 9. As the neighborhood size increases, what happens to Lucene’s top-N accuracy on this data?

ggplot(results %>% filter(Algorithm == "Lucene")) +
  aes(x=factor(NNbrs), y=TopN.nDCG) +
  geom_boxplot()

# 10. As the neighborhood size increases, what happens to Item-Item’s top-N accuracy on this data?

ggplot(results %>% filter(Algorithm == "ItemItem")) +
  aes(x=factor(NNbrs), y=TopN.nDCG) +
  geom_boxplot()

# 11. Which two algorithms produce the most diverse recommendations?

ggplot(results) +
  aes(x=Algorithm, y=TopN.TagEntropy) +
  geom_boxplot()

# 12. Does increasing the neighborhood size make Lucene provide more or less diverse recommendations?

ggplot(results %>% filter(Algorithm == "Lucene")) +
  aes(x=factor(NNbrs), y=TopN.TagEntropy) +
  geom_boxplot()

# 13. What is the top-N nDCG of Popular?

results %>%
  filter(Algorithm == "Popular") %>%
  summarise(topn_mean = mean(TopN.nDCG))

# 14. What is the best algorithm to deploy for top-N recommendation?

ggplot(results) +
  aes(x=Algorithm, y=TopN.nDCG) +
  geom_boxplot()