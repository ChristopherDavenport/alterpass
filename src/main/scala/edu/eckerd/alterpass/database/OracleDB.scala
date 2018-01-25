package edu.eckerd.alterpass.database

import doobie.implicits._
import doobie.hikari._
import cats.effect.IO

case class OracleDB(host: String, port: Int, sid: String, hikariTransactor: HikariTransactor[IO]) {

  def getPersonalEmails(username: String): IO[List[(String, String)]] = {
    val newUserName = if (username.endsWith("@eckerd.edu")) username else s"$username@eckerd.edu"


    val q =sql"""SELECT
             gPersonal.GOREMAL_EMAIL_ADDRESS as PERSONAL_EMAIL,
             gSchool.GOREMAL_EMAL_CODE as EMAIL_CODE
        FROM GOREMAL gSchool
        INNER JOIN
          GOREMAL gPersonal
            ON gSchool.GOREMAL_PIDM = gPersonal.GOREMAL_PIDM
        WHERE
          gSchool.GOREMAL_EMAL_CODE in ('CA', 'CAS', 'ECA')
        AND
          gSchool.GOREMAL_STATUS_IND = 'A'
        AND
          gPersonal.GOREMAL_EMAL_CODE = 'PR'
        AND
          gPersonal.GOREMAL_STATUS_IND = 'A'
        AND
          gSchool.GOREMAL_EMAIL_ADDRESS= $newUserName
      """.query[(String, String)]

      q.list.transact(hikariTransactor)
  }


}

object OracleDB {
  def createOracleTransactor(
                              host: String,
                              port: Int,
                              sid: String,
                              username: String,
                              password: String
                            ): IO[HikariTransactor[IO]] = {

    val oracle_driver = "oracle.jdbc.driver.OracleDriver"
    val oracle_connection_string = s"jdbc:oracle:thin:@//$host:$port/$sid"
    HikariTransactor[IO](oracle_driver,
      oracle_connection_string,
      username,
      password)
  }

  def build(
             host: String,
             port: Int,
             sid: String,
             username: String,
             password: String
           ): IO[OracleDB] = {
    createOracleTransactor(host, port, sid, username, password)
      .map(hikariTransactor => OracleDB(host, port, sid, hikariTransactor))
  }

}
