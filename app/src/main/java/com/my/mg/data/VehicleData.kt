package com.my.mg

/**
 * 车辆状态响应数据类
 * 从API获取的完整车辆状态信息
 */
data class VehicleStatusResponse(val req_id: String?, val data: VehicleData?)

/**
 * 车辆数据根节点
 * 包含位置、状态、数值等所有车辆信息
 */
data class VehicleData(
    val vehicle_position: VehiclePosition?,
    val vehicle_security: Any?,
    val vehicle_alerts: List<Any>?,
    val vehicle_value: VehicleValue?,
    val vehicle_state: VehicleState?,
    val update_time: Long?,
    var calculator:String?
)

/**
 * 车辆位置信息
 * GPS定位相关数据
 */
data class VehiclePosition(
    // 卫星数量
    val satellites: Int?, // 示例值: 3
    // 海拔高度(米)
    val altitude: Int?, // 示例值: 509
    // GPS状态 (2: 定位成功)
    val gps_status: Int?, // 示例值: 2
    // 纬度
    val latitude: String?, // 示例值: "30.676604"
    // 经度
    val longitude: String?, // 示例值: "103.886822"
    // 更新时间(毫秒时间戳)
    val update_time: Long?, // 示例值: 1769256718000
    // GPS时间(毫秒时间戳)
    val gps_time: Long?, // 示例值: 1769318263000
    // 水平精度因子
    val hdop: Int? // 示例值: 9
)

/**
 * 车辆数值信息
 * 包含所有可量化的车辆数据
 */
data class VehicleValue(
    // 后排右侧座椅通风等级
    val local_sec_row_r_seat_vent_lvl: Int?, // 示例值: null
    // 空调开关状态
    val climate_on_off_status: Int?, // 示例值: null
    // 方向盘加热等级
    val steer_heat_level: Int?, // 示例值: null
    // 电动尾门位置
    val pwr_lftgt_pos: Int?, // 示例值: null
    // 瞬时油耗
    val inst_fuel_consumption: Int?, // 示例值: null
    // 右后轮胎压(kPa)
    val rear_right_tyre_pressure: Int?, // 示例值: 220
    // T-BOX内部电池
    val tbox_internal_battery: Int?, // 示例值: null
    // 电池包故障状态
    val battery_pack_fault_status: Int?, // 示例值: null
    // 充电门位置状态
    val charge_door_position_status: Int?, // 示例值: null
    // 电动车续航显示命令
    val veh_elec_rng_dsp_cmd: Int?, // 示例值: 0
    // 后排左侧座椅通风等级
    val local_sec_row_l_seat_vent_lvl: Int?, // 示例值: null
    // BMS电池包SOC显示值
    val veh_bms_pack_soc_dsp_v: Int?, // 示例值: null
    // 电池格数
    val battery_lattice_number: Int?, // 示例值: 3
    // 续航里程(km)
    val driving_range: Int?, // 示例值: 229
    // 电池包续航(km)
    val battery_pack_range: Int?, // 示例值: 0
    // 电源模式
    val power_mode: Int?, // 示例值: 0
    // 发动机状态
    val engine_status: Int?, // 示例值: 0
    // 外部温度(°C)
    val exterior_temperature: Int?, // 示例值: 11
    // 后排右侧座椅加热原因
    val rmt_rr_seat_heat_flr_rsn: Int?, // 示例值: null
    // 燃油液位百分比(%)
    val fuel_level_prc: Int?, // 示例值: 27
    // 前排左侧座椅加热等级
    val localfront_left_seat_heat_level: Int?, // 示例值: null
    // 车内PM2.5
    val interior_pm25: Int?, // 示例值: 506
    // 里程表读数(km)
    val odometer: Int?, // 示例值: 30461
    // 后排左侧座椅加热等级
    val local_sec_row_l_seat_heat_lvl: Int?, // 示例值: null
    // 前排座椅加热原因
    val rmt_fr_seat_heat_flr_rsn: Int?, // 示例值: null
    // 远程座椅通风原因
    val rmt_seat_vent_flr_rsn: Int?, // 示例值: null
    // 检查状态
    val inspect_status: Int?, // 示例值: null
    // 远程方向盘加热等级
    val remote_steer_heat_level: Int?, // 示例值: null
    // 最后钥匙可见时间
    val last_key_seen: Int?, // 示例值: 0
    // 轮胎胎压监测状态
    val wheel_tyre_monitor_status: Int?, // 示例值: null
    // 远程空调异常原因
    val rmt_a_c_abot_rsn: Int?, // 示例值: null
    // 车辆电池电压(V)
    val vehicle_battery: Int?, // 示例值: 120
    // 充电剩余时间显示值
    val chrgng_rmnng_time: Int?, // 示例值: null
    // 车外PM2.5
    val exterior_pm25: Int?, // 示例值: 508
    // 左前轮胎压(kPa)
    val front_left_tyre_pressure: Int?, // 示例值: 244
    // 充电状态
    val charge_status: Int?, // 示例值: 0
    // 前排右侧座椅通风等级
    val localfront_right_seat_vent_level: Int?, // 示例值: null
    // 车速(km/h)
    val speed: Int?, // 示例值: 0
    // 车辆电池设备
    val vehicle_battery_dev: Int?, // 示例值: 3
    // 右前轮胎压(kPa)
    val front_right_tyre_pressure: Int?, // 示例值: 248
    // 电池包百分比(%)
    val battery_pack_prc: Int?, // 示例值: 0
    // 车内温度显示值
    val interior_temperature_v: Int?, // 示例值: null
    // 方向盘加热故障原因
    val steer_heat_failure_reason: Int?, // 示例值: null
    // 后排右侧座椅加热等级
    val local_sec_row_r_seat_heat_lvl: Int?, // 示例值: null
    // 仪表电动车续航显示值
    val clstr_elec_rng_to_eptv: Int?, // 示例值: null
    // 燃油续航(km)
    val fuel_range: Int?, // 示例值: 229
    // 当前行程ID
    val current_journey_id: Int?, // 示例值: 55
    // 电动尾门位置显示值
    val pwr_lftgt_pos_v: Int?, // 示例值: null
    // 车内温度(°C)
    val interior_temperature: Double?, // 示例值: 10
    // 前排右侧座椅加热等级
    val localfront_right_seat_heat_level: Int?, // 示例值: null
    // 航向角
    val heading: Int?, // 示例值: 0
    // 电池报警值数组
    val battery_alarm_value: List<Int>?, // 示例值: [50, 200, 500]
    // 更新时间(毫秒时间戳)
    val update_time: Long?, // 示例值: 1769504339000
    // 远程空调状态
    val remote_climate_status: Int?, // 示例值: 0
    // FOTA状态
    val fota_status: Int?, // 示例值: 0
    // 当前行程距离(km)
    val current_journey_distance: Int?, // 示例值: 0
    // BMS充电状态
    val bms_charge_status: Int?, // 示例值: null
    // 车辆报警状态
    val vehicle_alarm_status: Int?, // 示例值: 2
    // 车辆智能场景模式
    val veh_intlgnt_scene_md: Int?, // 示例值: null
    // 左后轮胎压(kPa)
    val rear_left_tyre_pressure: Int?, // 示例值: 244
    // 车辆电池百分比(%)
    val vehicle_battery_prc: Int?, // 示例值: 700
    // 电池类型
    val battery_type: Int?, // 示例值: 0
    // 燃油液位显示
    val fuel_level_disp: Int?, // 示例值: 3
    // 监测状态
    val monitor_status: Int?, // 示例值: null
    // 前排左侧座椅通风等级
    val localfront_left_seat_vent_level: Int?, // 示例值: null
)

/**
 * 车辆状态信息
 * 包含所有开关状态和布尔值信息
 */
data class VehicleState(
    // 车门状态(任意车门)
    val door: Boolean, // 示例值: false
    // GPS天线连接状态
    val gnss_ant_connected: Boolean, // 示例值: false
    // 后排左侧座椅加热
    val second_row_left_seat_heat: Boolean, // 示例值: false
    // 右后车门状态
    val rear_right_door: Boolean, // 示例值: false
    // 后排右侧座椅通风
    val second_row_right_seat_vent: Boolean, // 示例值: false
    // 近光灯状态
    val dipped_beam: Boolean, // 示例值: false
    // 驾驶员车窗状态
    val driver_window: Boolean, // 示例值: false
    // 仪表续航显示
    val clstr_range: Boolean, // 示例值: false
    // 车辆电池连接状态
    val vehicle_battery_connected: Boolean, // 示例值: true
    // 座椅加热状态
    val seat_heat: Boolean, // 示例值: false
    // 驾驶员车门状态
    val driver_door: Boolean, // 示例值: false
    // 车门锁定状态
    val lock: Boolean, // 示例值: true
    // 右侧座椅加热
    val right_seat_heat: Boolean, // 示例值: false
    // 反馈状态
    val feed: Boolean, // 示例值: false
    // 喇叭状态
    val horn: Boolean, // 示例值: false
    // 空气净化状态
    val air_clean: Boolean, // 示例值: false
    // 大灯状态
    val light: Boolean, // 示例值: false
    // 前引擎盖状态
    val bonnet: Boolean, // 示例值: false
    // 电池低压状态
    val bat_low: Boolean, // 示例值: null
    // 检查状态
    val inspect: Boolean, // 示例值: null
    // 方向盘加热状态
    val steer_heat: Boolean, // 示例值: false
    // 示宽灯状态
    val side_light: Boolean, // 示例值: false
    // GSM天线连接状态
    val gsm_ant_connected: Boolean, // 示例值: false
    // 后排左侧座椅通风
    val second_row_left_seat_vent: Boolean, // 示例值: false
    // 电池包状态
    val battery_pack: Boolean, // 示例值: true
    // 组合控制状态
    val combination_control: Boolean, // 示例值: false
    // 发动机状态
    val engine: Boolean, // 示例值: false
    // 座椅通风状态
    val seat_vent: Boolean, // 示例值: false
    // 左侧座椅通风
    val left_seat_vent: Boolean, // 示例值: false
    // 后排右侧座椅加热
    val second_row_right_seat_heat: Boolean, // 示例值: false
    // 电源状态
    val power: Boolean, // 示例值: false
    // 后备箱状态
    val boot: Boolean, // 示例值: false
    // 电源保护状态
    val power_protection: Boolean, // 示例值: false
    // 充电状态
    val charge: Boolean, // 示例值: false
    // 副驾驶车门状态
    val passenger_door: Boolean, // 示例值: false
    // CAN总线状态
    val can_bus: Boolean, // 示例值: true
    // 更新时间(毫秒时间戳)
    val update_time: Long, // 示例值: 1769504339000
    // 监测状态
    val monitor: Boolean, // 示例值: null
    // 空调状态
    val climate: Boolean, // 示例值: false
    // 右侧座椅通风
    val right_seat_vent: Boolean, // 示例值: false
    // 左侧座椅加热
    val left_seat_heat: Boolean, // 示例值: false
    // 右后车窗状态
    val rear_right_window: Boolean, // 示例值: false
    // 天窗状态
    val sunroof: Boolean, // 示例值: false
    // 左后车门状态
    val rear_left_door: Boolean, // 示例值: false
    // 副驾驶车窗状态
    val passenger_window: Boolean, // 示例值: false
    // 车窗状态(任意车窗)
    val window: Boolean, // 示例值: false
    // 左后车窗状态
    val rear_left_window: Boolean, // 示例值: false
    // 远光灯状态
    val main_beam: Boolean // 示例值: false
)