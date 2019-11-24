package main

import "time"

func mobileProcessLoop(ctx IContex) {
	ctx.mobile2deviceData.init()
	ctx.mobile2deviceData.sendStep = DATA_DEVICE_SEND_STEP
	for true {
		select {
		case msg := <-main2mobileChan:
			onMsgReceived(ctx, *msg)

		default:
			mobileStep(ctx)
		}

	}
}

func mobileStep(ctx IContex) {
	if DATA_STATE_SEND_REQUIRED == ctx.mobile2deviceData.state {
		data := ctx.mobile2deviceData.getSendData()
		dataSend(ctx, DIVICE, data)
		ctx.mobile2deviceData.state = DATA_STATE_SENDING
	} else if DATA_STATE_SENDING == ctx.mobile2deviceData.state {
		if time.Now().Unix()-ctx.mobile2deviceData.sendTime > DATA_RETRY_TIMEOUT && ctx.mobile2deviceData.retryCnt > 0 {
			data := ctx.mobile2deviceData.getLastSendData()
			dataSend(ctx, DIVICE, data)
			ctx.mobile2deviceData.retryCnt -= 1
		}
	}
}
