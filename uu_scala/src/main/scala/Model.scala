
object Model {
  case class RatingRaw(User: Int, movie_id: Int, rating_num: Option[Double])
  case class Rating(userId: Int, movieId: Int, rating: Double, ratingRounded: Double)
}
