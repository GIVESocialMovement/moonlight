DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

psql -d postgres -U postgres -p 5432 -a -f ${DIR}/db.sql
