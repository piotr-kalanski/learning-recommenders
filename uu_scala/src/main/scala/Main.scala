
import Model._
import com.datawizards.csv2class._

object Main extends App {
  val parsed = parseCSV[RatingRaw]("data_cleaned.csv")
  val ratings = parsed._1

  val users = ratings.map(_.User).toSeq.distinct
  val movies = ratings.map(_.movie_id).toSeq.distinct

  val ratingsByUser = users.map(u => u -> extractUserRatings(u, ratings)).toMap

  val correlations = (
    for {
      u1 <- users
      u2 <- users
    } yield (u1,u2) -> pearsonCorrelationScore(ratingsByUser(u1), ratingsByUser(u2))
  ).toMap

  val averageRatingsByUser = ratingsByUser.mapValues(ratings => ratings.values.sum / ratings.size).toMap

  //correlations.foreach(println)

  println(correlations((1648, 5136)))
  println(correlations((918, 2824)))

  println("=============\nTop users for 3712:")
  calculateTopUsers(3712).foreach(println)

  println("=============\nUser 3712 predictions:")
  calculatePredictions(3712).take(3).foreach(println)
  println("=============\nUser 3525 predictions:")
  calculatePredictions(3525).take(3).foreach(println)
  println("=============\nUser 3867 predictions:")
  calculatePredictions(3867).take(3).foreach(println)
  println("=============\nUser 89 predictions:")
  calculatePredictions(89).take(3).foreach(println)

  println("=============\nUser 3712 norm predictions:")
  calculateNormalizedPredictions(3712).take(3).foreach(println)
  println("=============\nUser 3525 norm predictions:")
  calculateNormalizedPredictions(3525).take(3).foreach(println)
  println("=============\nUser 3867 norm predictions:")
  calculateNormalizedPredictions(3867).take(3).foreach(println)
  println("=============\nUser 89 norm predictions:")
  calculateNormalizedPredictions(89).take(3).foreach(println)


  def extractUserRatings(user: Int, ratings: Iterable[RatingRaw]): Map[Int, Double] = {
    ratings
      .withFilter(r => r.User == user && r.rating_num.isDefined)
      .map(r => r.movie_id -> r.rating_num.get)
      .toMap
  }

  def commonMapKeys[A, B](a: Map[A, B], b: Map[A, B]): Set[A] = a.keySet.intersect(b.keySet)

  /**
    * Calculate the Pearson Correlation Score for two Maps of movie reviewer data.
    */
  def pearsonCorrelationScore(
                               u1Ratings: Map[Int, Double],
                               u2Ratings: Map[Int, Double]): Option[Double] = {

      // find the movies common to both reviewers
      val listOfCommonItems = commonMapKeys(u1Ratings, u2Ratings).toSeq
      val n = listOfCommonItems.size
      if (n == 0) return None

      // reduce the maps to only the movies both reviewers have seen
      val p1CommonRatings = u1Ratings.filterKeys(movie => listOfCommonItems.contains(movie))
      val p2CommonRatings = u2Ratings.filterKeys(movie => listOfCommonItems.contains(movie))

      // add up all the preferences
      val sum1 = p1CommonRatings.values.sum
      val sum2 = p2CommonRatings.values.sum

      // sum up the squares
      val sum1Sq = p1CommonRatings.values.foldLeft(0.0)(_ + Math.pow(_, 2))
      val sum2Sq = p2CommonRatings.values.foldLeft(0.0)(_ + Math.pow(_, 2))

      // sum up the products
      val pSum = listOfCommonItems.foldLeft(0.0)((accum, element) => accum + p1CommonRatings(element) * p2CommonRatings(element))

      // calculate the pearson score
      val numerator = pSum - (sum1*sum2/n)
      val denominator = Math.sqrt( (sum1Sq-Math.pow(sum1,2)/n) * (sum2Sq-Math.pow(sum2,2)/n))
      if (denominator == 0) None else Some(numerator/denominator)
  }

  def calculateTopUsers(user: Int): Iterable[Int] = {
    correlations
      .withFilter(p => p._1._1 == user && p._1._2 != user && p._2.isDefined)
      .map(p => p._1._2 -> p._2.get)
      .toSeq
      .sortBy(p => -p._2)
      .map(_._1)
      .take(5)
  }

  def calculatePredictions(user: Int): Iterable[Rating] = {
    val topUsers = calculateTopUsers(user)

    movies
      .map { movie =>
         val ratingsAndWeights =
           topUsers
            .withFilter(u => ratingsByUser(u).contains(movie))
            .map { otherUser =>
              val otherUserRatings = ratingsByUser(otherUser)
              val weight = correlations(user, otherUser).getOrElse(0.0)
              otherUserRatings.getOrElse(movie, 0.0) -> weight
            }

        val ratingWeightSum = ratingsAndWeights.map(rw => rw._1 * rw._2).sum
        val weightSum = ratingsAndWeights.map(rw => rw._2).sum
        val prediction = ratingWeightSum / weightSum
        Rating(user, movie, prediction, (prediction * 1000).round / 1000.0)
      }
      .sortBy(-_.rating)
  }

  def calculateNormalizedPredictions(user: Int): Iterable[Rating] = {
    val topUsers = calculateTopUsers(user)

    movies
      .map { movie =>
        val userRatingWeights =
          topUsers
            .withFilter(u => ratingsByUser(u).contains(movie))
            .map { otherUser =>
              val otherUserRatings = ratingsByUser(otherUser)
              val weight = correlations(user, otherUser).getOrElse(0.0)
              (otherUserRatings.getOrElse(movie, 0.0), weight, otherUser)
            }

        val ratingWeightSum = userRatingWeights.map(rw => (rw._1 - averageRatingsByUser(rw._3)) * rw._2).sum
        val weightSum = userRatingWeights.map(rw => rw._2).sum
        val prediction = averageRatingsByUser(user) + ratingWeightSum / weightSum
        Rating(user, movie, prediction, (prediction * 1000).round / 1000.0)
      }
      .sortBy(-_.rating)
  }

}
