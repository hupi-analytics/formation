curl -XPOST $1:9200/bank/account/_bulk?pretty --data-binary @accounts.json
