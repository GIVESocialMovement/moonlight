Test project for local Moonlight test
============================

1. Setup the database by running `./setup/setup_db.sh`. You might need to adjust some parameters in the script
2. Run `sbt run`, migrate the database, and add a job
3. Run `sbt 'runMain givers.moonlight.v2.MoonlightApplication dev'` to run Moonlight and see it in the single mode
4. Reload the webpage to see the change