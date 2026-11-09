

-- Update group_name for SHA-10-005 in benefits_master table

UPDATE benefits_master
SET group_name = '{
  "RehabRule": {
    "scope": "individual",
    "minAge": 18,
    "billingPolicy": {
      "type": "duration",
      "bands": [
        {
          "maxDays": 10,
          "minDays": 1,
          "ratePerDay": 1000
        },
        {
          "maxDays": 45,
          "minDays": 11,
          "flatAmount": 10000
        },
        {
          "minDays": 46,
          "flatAmount": 67200
        }
      ]
    }
  }
}'
WHERE intervention_code = 'SHA-10-005';
