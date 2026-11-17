-- SCD Type 2 demo: current row + prior risk_tier version for users with updates
with current_users as (
    select
        {{ generate_surrogate_key(['user_id', 'updated_at']) }} as user_sk,
        user_id,
        email,
        country,
        kyc_level,
        risk_tier,
        created_at,
        updated_at as valid_from,
        cast('2099-12-31 00:00:00' as DateTime64(3, 'UTC')) as valid_to,
        1 as is_current
    from {{ ref('stg_users') }}
),
prior_versions as (
    select
        {{ generate_surrogate_key(['user_id', "'prior'"]) }} as user_sk,
        user_id,
        email,
        country,
        kyc_level,
        multiIf(
            risk_tier = 'high', 'medium',
            risk_tier = 'medium', 'low',
            'low'
        ) as risk_tier,
        created_at,
        created_at as valid_from,
        updated_at as valid_to,
        0 as is_current
    from {{ ref('stg_users') }}
    where updated_at > created_at
)
select * from current_users
union all
select * from prior_versions
