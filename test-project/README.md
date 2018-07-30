Test project for Moonlight
============================

1. Setup the database by running `./setup/setup_db.sh`. You might need to adjust some parameters in the script.
2. Run `sbt run`, migrate the database, and add a job
3. Run `sbt 'runMain givers.moonlight.Main dev'` to run Moonlight and see it in action.
4. Reload the webpage to see the change.

Deploy to Heroku: ```git push heroku `git subtree split --prefix test-project`:master -f```

See the demo: https://moonlight-test.herokuapp.com (don't forget to turn on the worker dyno).
