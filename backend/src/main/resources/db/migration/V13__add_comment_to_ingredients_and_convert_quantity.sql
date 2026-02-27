UPDATE recipes
SET data = jsonb_set(data, '{ingredients}',
                     (SELECT jsonb_agg(
                                     CASE
                                         WHEN elem ->> 'quantity' IS NOT NULL
                                             AND elem ->> 'quantity' ~ '^\s*[+-]?(\d+\.?\d*|\.\d+)\s*$'
                                             THEN jsonb_strip_nulls(jsonb_build_object('name', elem -> 'name',
                                                                                       'quantity',
                                                                                       (elem ->> 'quantity')::numeric,
                                                                                       'unit', elem -> 'unit'))
                                         WHEN elem ->> 'quantity' IS NOT NULL
                                             THEN jsonb_strip_nulls(jsonb_build_object('name', elem -> 'name', 'unit',
                                                                                       elem -> 'unit', 'comment',
                                                                                       elem -> 'quantity'))
                                         ELSE jsonb_strip_nulls(jsonb_build_object('name', elem -> 'name', 'unit', elem -> 'unit'))
                                         END
                             )
                      FROM jsonb_array_elements(data -> 'ingredients') AS elem))
WHERE data ? 'ingredients'
  AND jsonb_array_length(data -> 'ingredients') > 0;
