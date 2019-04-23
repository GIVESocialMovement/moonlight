Test project for Moonlight
============================

1. Setup the database by running `./setup/setup_db.sh`. You might need to adjust some parameters in the script.
2. Run `sbt run`, migrate the database, and add a job
3. Run `sbt 'runMain givers.moonlight.Main dev run'` to run Moonlight and see it in the single mode. Or `runMain givers.moonlight.Main dev coordinate` to run the Moonlight in the coordination mode.
4. Reload the webpage to see the change.

Deploy to Heroku: ```sbt clean stage deployHeroku```

See the demo: https://moonlight-test.herokuapp.com (don't forget to turn on the worker dyno).
