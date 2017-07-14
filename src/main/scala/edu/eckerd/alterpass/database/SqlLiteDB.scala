package edu.eckerd.alterpass.database

import doobie.imports._
import fs2.Task
import fs2.Strategy
import fs2.interop.cats._
import cats.implicits._

case class SqlLiteDB(transactor: Transactor[Task, Unit]) {

  def rateLimitCheck(username: String, time: Long): Task[Boolean] = {
    // 1 Day Less Than Current Time
    val minTimeEpoch = time - 900L
    val query = sql"""SELECT
                  username
                  FROM FORGOT_PASSWORD
                  WHERE
                  username = $username
                  AND
                  created >= $minTimeEpoch
                  """.query[String]

    query.list.transact(transactor).map(_.headOption.isEmpty)
  }


  def writeConnection(username: String, random: String, time: Long): Task[Int] = {
    val insert =sql"""INSERT INTO FORGOT_PASSWORD (username, linkExtension, created)
            VALUES (
              $username,
              $random,
              $time
            )
         """.update

    insert.run.transact(transactor)
  }

  def recoveryLink(username: String, url: String, time: Long): Task[Boolean] = {
    // 1 Day Less Than Current Time
    val minTimeEpoch = time - 86400L
    val query = sql"""SELECT
                  username
                  FROM FORGOT_PASSWORD
                  WHERE
                  linkExtension = $url
                  AND
                  username = $username
                  AND
                  created >= $minTimeEpoch
                  """.query[String]

    query.list.transact(transactor).map(_.headOption.isDefined)
  }

  def removeRecoveryLink(username: String, url: String): Task[Int] = {
    val update = sql"""DELETE FROM FORGOT_PASSWORD
                  WHERE
                  linkExtension = $url
                  AND
                  username = $username
                  """.update

    update.run.transact(transactor)
  }

  def removeOlder(time: Long): Task[Int] = {
    val minTimeEpoch = time - 86400L
    val update : Update0 = sql"""DELETE FROM FORGOT_PASSWORD
                  WHERE
                  created < $minTimeEpoch
                  """.update
    update.run.transact(transactor)
  }


}

object SqlLiteDB {

  val dbName = "alterpass.db"

  def createSqlLiteTransactor(path: String): Transactor[Task, Unit] = {

    val newPath = if (path.endsWith("/")) path else s"$path/"
    val sqlliteDriver = "org.sqlite.JDBC"
    val connectionString = s"jdbc:sqlite:$newPath$dbName"
    println(connectionString)

    DriverManagerTransactor[Task](
      sqlliteDriver, connectionString
    )
  }

  /**
    * Requires sqlite3 to be installed on System
    *
    * @param path The Path To The Location Where the Database Should Be Created
    * @param strategy The Execution Strategy
    * @return A SqlLiteDb Ready for Use
    */
  def build(path: String)(implicit strategy: Strategy): Task[SqlLiteDB] = {
    val newPath = if (path.endsWith("/")) path else s"$path/"
//    val createDB = Task(s"sqlite3 $newPath$dbName".!)

    val transactor = createSqlLiteTransactor(newPath)


    val createTableT = createTable
      .run
      .transact(transactor)

    transactor.connect(()) >> createTableT >> Task(SqlLiteDB(transactor))
  }



  val createTable: Update0 =
    sql"""
       CREATE TABLE IF NOT EXISTS FORGOT_PASSWORD (
        username TEXT,
        linkExtension TEXT,
        created INTEGER
       )
       """.update

}
