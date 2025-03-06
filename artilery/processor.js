const fs = require('fs');
const path = require('path');

// Create logs directory if it doesn't exist
const logsDir = path.join(__dirname, 'logs');
if (!fs.existsSync(logsDir)){
    fs.mkdirSync(logsDir);
}

// Create a log file with timestamp
const logFile = path.join(logsDir, `artillery-test-xyz4.log`);

function logResponse(req, res, context, ee, next) {
    const entry = {
        timestamp: new Date().toISOString(),
        request: {
            userId: context.vars.userId,
            email: context.vars.email,
            couponCode: "xyz4"
        },
        response: {
            statusCode: res.statusCode,
            body: context.vars.response
        }
    };

    // Append to log file
    fs.appendFileSync(logFile, JSON.stringify(entry, null, 2) + '\n');

    return next();
}

module.exports = {
    logResponse
};