@Grab('io.github.http-builder-ng:http-builder-ng-core:1.0.3')
import static groovyx.net.http.HttpBuilder.configure
import static groovyx.net.http.ContentTypes.JSON
import groovyx.net.http.*
import static groovy.json.JsonOutput.*

// magic to get jenkins build properties
def build = this.getProperty('binding').getVariable('build')
def listener = this.getProperty('binding').getVariable('listener')
def env = build.getEnvironment(listener)
println "Environment Variables:"
println prettyPrint(toJson(env))


def base = env['PAGURE_SITE']
def repo = env['PAGURE_REPO']
def pr = env['PAGURE_PR']
def token = env['TOKEN']
def percent = env['PERCENT_PASSED']
def comment = env['COMMENT']
if (!comment?.trim()) {
  if (percent == '0')  comment = 'Build failed.'
  else if (percent == '100') comment = 'Build passed.'
}
def url = env['REFERENCE_URL']
def job = env['REFERENCE_JOB_NAME']

// if anything is not set by this point fail it
if (!base?.trim() ||
    !repo?.trim() ||
    !pr?.trim() ||
    !token?.trim() ||
    !percent?.trim() ||
    !comment?.trim() ||
    !url?.trim() ||
    !job?.trim() ) { throw new Exception("Not all args set.") }


def payload = [ 'username': 'Jenkins',
                'percent' : ( percent as Integer ),
                'comment' : comment,
                'url'     : url,
                'uid'     : job
              ]
println "Sending Args:"
println payload

def pagureUrl = "/api/0/${repo}/pull-request/${pr}/flag"
def result = configure {
  request.uri = base
  request.contentType = JSON[0]
  request.headers['Authorization'] = "token ${token}"
}.post {
  request.uri.path = pagureUrl
  request.body = payload
  request.contentType = 'application/x-www-form-urlencoded'
  request.encoder 'application/x-www-form-urlencoded', NativeHandlers.Encoders.&form
}

println prettyPrint(toJson(result))
