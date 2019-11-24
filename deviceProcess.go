package main

import "time"

func deviceProcessLoop(ctx IContex) {
	ctx.device2mobileData.init()
	ctx.device2mobileData.sendStep = DATA_MOBILE_SEND_STEP
	for true {
		select {
		case msg := <-main2deviceChan:
			onMsgReceived(ctx, *msg)

		default:
			deviceStep(ctx)
		}

	}
}

func deviceStep(ctx IContex) {
	if DATA_STATE_SEND_REQUIRED == ctx.device2mobileData.state {
		data := ctx.device2mobileData.getSendData()
		dataSend(ctx, MOBILE, data)
		ctx.device2mobileData.state = DATA_STATE_SENDING
	} else if DATA_STATE_SENDING == ctx.device2mobileData.state {
		if time.Now().Unix()-ctx.device2mobileData.sendTime > DATA_RETRY_TIMEOUT && ctx.device2mobileData.retryCnt > 0 {
			data := ctx.device2mobileData.getLastSendData()
			dataSend(ctx, MOBILE, data)
			ctx.device2mobileData.retryCnt -= 1
		}
	}
}
